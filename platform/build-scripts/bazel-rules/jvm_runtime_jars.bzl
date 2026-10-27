"""The runtime jars of JVM modules as runfiles, for a build-scripts binary that reads module outputs."""

load("@rules_java//java:defs.bzl", "JavaInfo")

def _jvm_runtime_jars_impl(ctx):
    jars = depset(transitive = [target[JavaInfo].transitive_runtime_jars for target in ctx.attr.modules])
    return [DefaultInfo(files = depset(), runfiles = ctx.runfiles(transitive_files = jars))]

jvm_runtime_jars = rule(
    doc = """The runtime jars of `modules` and of all their runtime dependencies, as runfiles.

    A build-scripts binary names this target as its data when it reads the runtime classpath of some modules. Under
    `bazel run`, `BazelModuleOutputProvider` finds each jar in the runfiles tree by its label. A library jar it finds by
    its label or by its container file.
    """,
    implementation = _jvm_runtime_jars_impl,
    attrs = {
        "modules": attr.label_list(
            doc = "The module targets. Each one publishes `JavaInfo`.",
            providers = [JavaInfo],
        ),
    },
)
