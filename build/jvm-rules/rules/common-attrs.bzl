load("@rules_java//java:defs.bzl", "JavaInfo")
load(
    "@rules_kotlin//kotlin/internal:defs.bzl",
    _JAVA_TOOLCHAIN_TYPE = "JAVA_TOOLCHAIN_TYPE",
    _KtCompilerPluginInfo = "KtCompilerPluginInfo",
    _KtJvmInfo = "KtJvmInfo",
    _KtPluginConfiguration = "KtPluginConfiguration",
    _TOOLCHAIN_TYPE = "TOOLCHAIN_TYPE",
)
load(
    "@rules_kotlin//kotlin/internal:opts.bzl",
    _JavacOptions = "JavacOptions",
)
load(
    "//:rules/impl/kotlinc-options.bzl",
    "KotlincOptions",
)
load("//:rules/impl/transitions.bzl", "scrubbed_host_platform_transition")

visibility("private")

common_toolchains = [
    _TOOLCHAIN_TYPE,
    _JAVA_TOOLCHAIN_TYPE,
]

def add_dicts(*dictionaries):
    result = {}
    for d in dictionaries:
        result.update(d)
    return result

_implicit_deps = {
    "_java_toolchain": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_toolchain"),
    ),
    "_tool_java_runtime": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
        cfg = "exec",
    ),
}

common_attr = add_dicts(
    _implicit_deps,
    {
        "deps": attr.label_list(
            doc = """A list of dependencies of this rule.See general comments about `deps` at
        [Attributes common to all build rules](https://docs.bazel.build/versions/master/be/common-definitions.html#common-attributes).""",
            providers = [
                [JavaInfo],
                [_KtJvmInfo],
            ],
            allow_files = False,
        ),
        "runtime_deps": attr.label_list(
            doc = """Libraries to make available to the final binary or test at runtime only. Like ordinary deps, these will
        appear on the runtime classpath, but unlike them, not on the compile-time classpath.""",
            default = [],
            allow_files = False,
        ),
        "data": attr.label_list(
            doc = """The list of files needed by this rule at runtime. See general comments about `data` at
        [Attributes common to all build rules](https://docs.bazel.build/versions/master/be/common-definitions.html#common-attributes).""",
            allow_files = True,
        ),
        "associates": attr.label_list(
            doc = """Kotlin deps who should be considered part of the same module/compilation-unit
            for the purposes of "internal" access. Such deps must all share the same module space
            and so a target cannot associate to two deps from two different modules.""",
            default = [],
            providers = [JavaInfo, _KtJvmInfo],
        ),
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
        "javac_opts": attr.label(
            doc = """Javac options to be used when compiling this target. These opts if provided will
            be used instead of the ones provided to the toolchain.
            Use --@rules_jvm:default-kotlinc-opts=//:my-custom-settings to point to a custom global default options in .bazelrc
            """,
            default = None,
            providers = [_JavacOptions],
        ),
        "_jvm_builder": attr.label(
            default = "//:jvm-builder",
            allow_single_file = True,
            cfg = scrubbed_host_platform_transition,
        ),
        "_jvm_builder_jvm_flags": attr.label(
            default = "//:jvm-builder-jvm_flags",
        ),
        "_reduced_classpath": attr.bool(default = False),
        "_trace": attr.label(default = "//:trace"),
        "_kotlin_inc_threshold": attr.label(default = "//:koltin_inc_threshold"),
        "_java_inc_threshold": attr.label(default = "//:java_inc_threshold"),
    },
)

# we cannot use attr.output because it may break compatibility (requesting default output as `.jar` or `-sources.jar`), including IntelliJ IDEA plugin
common_outputs = dict(
    jar = "%{name}.jar",
    srcjar = "%{name}-sources.jar",
)
