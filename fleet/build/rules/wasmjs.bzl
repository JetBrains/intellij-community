load("@rules_jvm//:wasmjs.bzl", "wasmjs_binary", "wasmjs_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtCompilerPluginInfo = "KtCompilerPluginInfo")

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

    wasmjs_main_sourceset_target_name = "%s_wasmJsMain" % name
    native.filegroup(
        name = wasmjs_main_sourceset_target_name,
        srcs = native.glob(["srcWasmJsMain/**/*.kt"], allow_empty = True),
    )

    # TODO: wire `wasmjs_test` using `test_deps` once that rule is introduced in the monorepo

    wasmjs_library(
        name = "%s_lib" % name,
        module_name = module_name,
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
