load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_jvm//:wasmjs.bzl", "KtWasmJsInfo", "wasmjs_binary", "wasmjs_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KOTLIN_TOOLCHAIN = "TOOLCHAIN_TYPE", _KtCompilerPluginInfo = "KtCompilerPluginInfo")
load("//fleet/build/rules:haven_cli.bzl", "HAVEN_CLI_ATTR", "run_haven_cli")

def _fleet_wasmjs_service_accessors_impl(ctx):
    output = ctx.actions.declare_file("%s.kt" % ctx.label.name)

    if not ctx.files.srcs:
        ctx.actions.write(output, "// No sources in module `%s`, no Fleet services to declare.\n" % ctx.attr.module_name)
        return [DefaultInfo(files = depset([output]))]

    # Mirrors the module's WasmJS compilation: the compile-visible klibs of its `deps` + `exports`.
    compile_klibs = depset(
        transitive = [
            dep[KtWasmJsInfo].compile_klibs
            for dep in ctx.attr.deps
        ],
    )
    processor_classpath = ctx.attr._kernel_plugins_processor[JavaInfo].transitive_runtime_jars
    kotlin_toolchain = ctx.toolchains[_KOTLIN_TOOLCHAIN]
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("generate-fleet-wasm-service-accessors")
    args.add("--module-name=%s" % ctx.attr.module_name)
    args.add_all(["--sources=%s" % s.path for s in ctx.files.srcs])
    args.add_all(["--classpath=%s" % c.path for c in compile_klibs.to_list()])
    args.add_all(["--processor-classpath=%s" % c.path for c in processor_classpath.to_list()])
    args.add("--language-version=%s" % kotlin_toolchain.language_version)
    args.add("--api-version=%s" % kotlin_toolchain.api_version)
    args.add("--output-file=%s" % output.path)

    run_haven_cli(
        ctx = ctx,
        mnemonic = "GenerateFleetWasmServiceAccessors",
        inputs = depset(
            direct = ctx.files.srcs,
            transitive = [compile_klibs, processor_classpath],
        ),
        outputs = [output],
        arguments = [args],
        progress_message = "Generating Fleet WasmJS service accessors for %%{label}",
    )

    return [DefaultInfo(files = depset([output]))]

_fleet_wasmjs_service_accessors = rule(
    doc = """Generates the `@JsExport fun <module>_findServices_<service>()` accessors of a WasmJS module
      (`ServiceProvider.wasm.kt`), the runtime service discovery mechanism of `FleetModuleLayer.wasm.kt`. This is
      the Bazel counterpart of the KSP wiring of the Gradle WasmJS compilations.""",
    implementation = _fleet_wasmjs_service_accessors_impl,
    attrs = HAVEN_CLI_ATTR | {
        "srcs": attr.label_list(
            allow_files = True,
            mandatory = True,
            doc = "Source files analyzed for Fleet service implementations.",
        ),
        "deps": attr.label_list(
            providers = [[KtWasmJsInfo]],
            doc = "The module's WasmJS dependencies; their compile-visible klibs are the analysis classpath.",
        ),
        "module_name": attr.string(
            mandatory = True,
            doc = "The Fleet module name; the runtime looks accessors up by the sanitized form of this name.",
        ),
        "_kernel_plugins_processor": attr.label(
            default = "//fleet/build/kernel.plugins.processor",
            providers = [JavaInfo],
        ),
    },
    toolchains = [_KOTLIN_TOOLCHAIN],
)

# TODO: make it a symbolic macro if we manage to work around the usage of globs
def fleet_wasmjs_module(name, visibility, module_name, kotlinc_opts, deps = [], test_deps = [], exports = [], test_exported_deps = [], runtime_deps = [], plugins = []):
    common_main_sourceset_target_name = "%s_commonMain" % name
    native.filegroup(
        name = common_main_sourceset_target_name,
        srcs = native.glob([
            "genCommonMain/**/*.kt",
            "srcCommonMain/**/*.kt",
        ], allow_empty = True),
    )

    analyzed_sources = native.glob([
        "genCommonMain/**/*.kt",
        "srcCommonMain/**/*.kt",
        "srcWasmJsMain/**/*.kt",
    ], allow_empty = True)
    wasmjs_main_sources = native.glob(["srcWasmJsMain/**/*.kt"], allow_empty = True)

    # Source-less modules (pure dependency aggregators) compile nothing: they can declare no services, and adding
    # the generated file would turn their no-op compilation into a real one without a stdlib dependency.
    if analyzed_sources:
        service_accessors_target_name = "%s_service_accessors" % name
        _fleet_wasmjs_service_accessors(
            name = service_accessors_target_name,
            module_name = module_name,
            srcs = analyzed_sources,
            deps = deps + exports,
        )
        wasmjs_main_sources = wasmjs_main_sources + [service_accessors_target_name]

    wasmjs_main_sourceset_target_name = "%s_wasmJsMain" % name
    native.filegroup(
        name = wasmjs_main_sourceset_target_name,
        srcs = wasmjs_main_sources,
    )

    # TODO: wire `wasmjs_test` using `test_deps` once that rule is introduced in the monorepo

    wasmjs_library(
        name = "%s_lib" % name,
        module_name = module_name,
        # The Gradle KMP build emits modules as `fleet.build-<jpsModuleName>.*` (root project name prefix);
        # fleet.dock.bootstrapWasm's dynamicImport and the webpack config key on that naming.
        ir_output_name = "fleet.build-%s" % module_name,
        fragment_refines = {"commonMain": [], "wasmJsMain": ["commonMain"]},
        visibility = visibility,
        fragment_sources = {"commonMain": common_main_sourceset_target_name, "wasmJsMain": wasmjs_main_sourceset_target_name},
        deps = deps,
        exports = exports,
        runtime_deps = runtime_deps,
        plugins = plugins,
        kotlinc_opts = kotlinc_opts,
    )

def fleet_wasmjs_binary(name, visibility, module_name, kotlinc_opts, module = None, optimize = "auto"):
    """Links (and optionally wasm-opt-optimizes) a fleet_wasmjs_module into a WasmJS application directory.

    By default links the `:wasmjs_module_lib` target the fleet_wasmjs_module macro of the same
    package creates (keep the `_lib` suffix in sync with fleet_wasmjs_module and
    fleet/build/generator DependenciesGeneratorExtensions.kt).
    """
    wasmjs_binary(
        name = name,
        module = module or ":wasmjs_module_lib",
        module_name = module_name,
        ir_output_name = module_name,
        kotlinc_opts = kotlinc_opts,
        optimize = optimize,
        visibility = visibility,
    )
