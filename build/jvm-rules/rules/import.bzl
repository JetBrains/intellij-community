load("@rules_java//java:defs.bzl", "JavaInfo")

visibility("private")

def _jvm_import(ctx):
    return JavaInfo(
        output_jar = ctx.file.jar,
        compile_jar = ctx.file.jar,
        source_jar = ctx.file.source_jar,
        runtime_deps = [dep[JavaInfo] for dep in ctx.attr.runtime_deps],
    )

jvm_import = rule(
    attrs = {
        "jar": attr.label(
            doc = """The jar listed here is equivalent to an export attribute.""",
            allow_single_file = True,
        ),
        "source_jar": attr.label(
            doc = """The sources for the class jar.""",
            allow_single_file = True,
        ),
        "runtime_deps": attr.label_list(
            doc = """Libraries to make available to the final binary or test at runtime only. Like ordinary deps, these will
        appear on the runtime classpath, but unlike them, not on the compile-time classpath.""",
            default = [],
            allow_files = False,
            providers = [JavaInfo],
        ),
    },
    provides = [JavaInfo],
    implementation = _jvm_import,
)
