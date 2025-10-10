load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo", _KtPluginConfiguration = "KtPluginConfiguration")
load("//:rules/common-attrs.bzl", "add_dicts", "common_toolchains", "kmp_attr")
load("//:rules/impl/compile-wasmjs.bzl", "KtWasmJsBin", "KtWasmJsInfo", "wasmjs_produce_module_actions")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition", "scrubbed_host_platform_transition")

visibility("private")

def _wasmjs_library(ctx):
    if ctx.attr.neverlink and ctx.attr.runtime_deps:
        fail("runtime_deps and neverlink is nonsensical.", attr = "runtime_deps")

    return wasmjs_produce_module_actions(ctx, "wasmjs_library")

wasmjs_library = rule(
    doc = """This rule compiles and links Kotlin and Java sources into a .jar file.""",
    attrs = add_dicts(kmp_attr, {
        "fragment_sources": attr.string_keyed_label_dict(
            doc = "Maps fragment names to their source targets",
            # allow_files = [".kt"],  # TODO: cannot specify that as we want to allow label that matches nothing as well
        ),
        # "fragment_platforms": attr.string_list_dict(
        #     doc = "Maps fragment names to their target platforms",
        #     default = {
        #         "commonMain": ["wasmJs", "jvm"],
        #         "commonTest": ["wasmJs", "jvm"],
        #         "wasmJsMain": ["wasmJs"],
        #         "wasmJsTest": ["wasmJs"],
        #         # "jvmMain": ["jvm"],
        #         # "jvmTest": ["jvm"],
        #     },
        # ),
        "fragment_refines": attr.string_list_dict(
            doc = "Maps fragment names to fragments they refine",
            default = {},
            # we do not default to the usual KMP refines, as this is a user-facing detail from the perspective of the compiler
        ),
        # "fragment_friends": attr.string_list_dict(
        #     doc = "Maps fragment names to fragments their friend fragments",
        #     default = {
        #         "commonMain": [],
        #         "commonTest": ["commonMain"],
        #         "wasmJsMain": [],
        #         "wasmJsTest": ["wasmJsMain"],
        #         # "jvmMain": ["commonMain"],
        #         # "jvmTest": ["commonTest"],
        #     },
        # ),
        "deps": attr.label_list(
            doc = """A list of dependencies of this rule.See general comments about `deps` at
              [Attributes common to all build rules](https://docs.bazel.build/versions/master/be/common-definitions.html#common-attributes).""",
            providers = [[KtWasmJsInfo], [KtWasmJsInfo, _KtJvmInfo]],
            allow_files = False,
        ),
        "exports": attr.label_list(
            doc = """\
            Exported libraries.

            Deps listed here will be made available to other rules, as if the parents explicitly depended on
            these deps. This is not true for regular (non-exported) deps.""",
            default = [],
            providers = [[KtWasmJsInfo], [KtWasmJsInfo, _KtJvmInfo]],
        ),
        "_wasmjs_builder": attr.label(
            default = "//src/kotlin/kotlin-builder-wasmjs:kotlin-builder-wasmjs_deploy.jar",
            allow_single_file = True,
            cfg = scrubbed_host_platform_transition,
        ),
        "_wasmjs_builder_jvm_flags": attr.label(
            default = "//src/kotlin/kotlin-builder-wasmjs:kotlin-builder-wasmjs-jvm_flags",
        ),
        "_wasmjs_builder_launcher": attr.label(
            default = "//:rules/impl/MemoryLauncher.java",
            allow_single_file = True,
        ),
        "neverlink": attr.bool(
            doc = """If true only use this library for compilation and not at runtime.""",
            default = False,
        ),
    }),
    toolchains = common_toolchains,
    implementation = _wasmjs_library,
    provides = [KtWasmJsInfo, KtWasmJsBin, _KtJvmInfo],
    cfg = jvm_platform_transition,
)
