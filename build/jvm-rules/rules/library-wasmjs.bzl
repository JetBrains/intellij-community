load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo", _KtPluginConfiguration = "KtPluginConfiguration")
load("//:rules/common-attrs.bzl", "add_dicts", "common_attr", "common_toolchains")
load("//:rules/impl/compile-wasmjs.bzl", "KtWasmJsBin", "KtWasmJsInfo", "kt_wasmjs_produce_module_actions")
load("//:rules/impl/kotlinc-options.bzl", "KotlincOptions")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition", "scrubbed_host_platform_transition")

visibility("private")

def _kt_wasmjs_library(ctx):
    if ctx.attr.neverlink and ctx.attr.runtime_deps:
        fail("runtime_deps and neverlink is nonsensical.", attr = "runtime_deps")

    return kt_wasmjs_produce_module_actions(ctx, "kt_wasmjs_library")

kt_wasmjs_library = rule(
    doc = """This rule compiles and links Kotlin and Java sources into a .jar file.""",
    attrs = add_dicts(common_attr, {
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
        "runtime_deps": attr.label_list(
            doc = """never used""",
            default = [],
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
        # "data": attr.label_list(
        #     doc = """The list of files needed by this rule at runtime. See general comments about `data` at
        #       [Attributes common to all build rules](https://docs.bazel.build/versions/master/be/common-definitions.html#common-attributes).""",
        #     allow_files = True,
        # ),
        "plugins": attr.label_list(
            default = [],
            cfg = "exec",
            providers = [
                [_KtPluginConfiguration],
                [_KtCompilerPluginInfo],
            ],
        ),
        "module_name": attr.string(
            doc = """The name of the module, if not provided the module name is derived from the label. --e.g.,
              `//some/package/path:label_name` is translated to
              `some_package_path-label_name`.""",
        ),
        "kotlinc_opts": attr.label(
            doc = """Kotlinc options to be used when compiling this target.""",
            default = "//:default-kotlinc-opts",
            providers = [KotlincOptions],
        ),
        "_wasmjs_builder": attr.label(
            default = "//src/kotlin/kotlin-builder-wasmjs:kotlin-builder-wasmjs",
            executable = True,
            #             allow_single_file = True,
            cfg = "exec",
            # cfg = scrubbed_host_platform_transition,
        ),
        "neverlink": attr.bool(
            doc = """If true only use this library for compilation and not at runtime.""",
            default = False,
        ),
    }),
    toolchains = common_toolchains,
    implementation = _kt_wasmjs_library,
    provides = [KtWasmJsInfo, KtWasmJsBin, _KtJvmInfo],
)
