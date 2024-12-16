load("@rules_java//java:defs.bzl", _JavaInfo = "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//:rules/common-attrs.bzl", "add_dicts", "common_attr", "common_outputs", "common_toolchains")
load("//:rules/impl/compile.bzl", "kt_jvm_produce_jar_actions")

visibility("private")

_lib_common_attr = add_dicts(common_attr, {
    "srcs": attr.label_list(
        doc = """The list of source files that are processed to create the target, this can contain both Java and Kotlin
         files. Java analysis occurs first so Kotlin classes may depend on Java classes in the same compilation unit.""",
        default = [],
        allow_files = [".kt", ".java"],
        mandatory = True,
    ),
    "exports": attr.label_list(
        doc = """\
Exported libraries.

Deps listed here will be made available to other rules, as if the parents explicitly depended on
these deps. This is not true for regular (non-exported) deps.""",
        default = [],
        providers = [JavaInfo],
    ),
    #     "exported_compiler_plugins": attr.label_list(
    #         doc = """\
    # Exported compiler plugins.
    #
    # Compiler plugins listed here will be treated as if they were added in the plugins attribute
    # of any targets that directly depend on this target. Unlike `java_plugin`s exported_plugins,
    # this is not transitive""",
    #         default = [],
    #         providers = [[_KtCompilerPluginInfo], [KtPluginConfiguration]],
    #     ),
    "neverlink": attr.bool(
        doc = """If true only use this library for compilation and not at runtime.""",
        default = False,
    ),
    "_empty_jar": attr.label(
        doc = """Empty jar for exporting JavaInfos.""",
        allow_single_file = True,
        cfg = "target",
        default = Label("@rules_kotlin//third_party:empty.jar"),
    ),
    "_empty_jdeps": attr.label(
        doc = """Empty jdeps for exporting JavaInfos.""",
        allow_single_file = True,
        cfg = "target",
        default = Label("@rules_kotlin//third_party:empty.jdeps"),
    ),
})

def _make_providers(ctx, providers):
    files = [ctx.outputs.jar]
    if providers.java.outputs.jdeps:
        files.append(providers.java.outputs.jdeps)

    return [
        providers.java,
        providers.kt,
        providers.instrumented_files,
        DefaultInfo(
            files = depset(files),
            runfiles = ctx.runfiles(
                # explicitly include data files, otherwise they appear to be missing
                files = ctx.files.data,
                transitive_files = depset(order = "default"),
                # continue to use collect_default until proper transitive data collecting is implemented.
                collect_default = True,
            ),
        ),
    ]

def _jvm_library(ctx):
    if ctx.attr.neverlink and ctx.attr.runtime_deps:
        fail("runtime_deps and neverlink is nonsensical.", attr = "runtime_deps")

    return _make_providers(ctx, kt_jvm_produce_jar_actions(ctx, "kt_jvm_library"))

jvm_library = rule(
    doc = """This rule compiles and links Kotlin and Java sources into a .jar file.""",
    attrs = _lib_common_attr,
    outputs = common_outputs,
    toolchains = common_toolchains,
    fragments = ["java"],  # required fragments of the target configuration
    host_fragments = ["java"],  # required fragments of the host configuration
    implementation = _jvm_library,
    provides = [_JavaInfo, _KtJvmInfo],
)
