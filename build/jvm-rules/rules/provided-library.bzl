load("@rules_java//java:defs.bzl", "JavaInfo")

visibility("private")

# rules_java creates empty JAR for each java_library target,
# so, we use rules_kotlin approach - use shared empty jar as output_jar (required attribute for JavaInfo)
def _jvm_provided_library(ctx):
    return JavaInfo(
        output_jar = ctx.file._empty_jar,
        compile_jar = None,
        exports = [ctx.attr.lib[JavaInfo]],
        neverlink = True,
    )

jvm_provided_library = rule(
    attrs = {
        "lib": attr.label(mandatory = True, allow_files = False, providers = [JavaInfo]),
        "_empty_jar": attr.label(
            doc = """Empty jar for exporting JavaInfos.""",
            allow_single_file = True,
            default = Label("@rules_kotlin//third_party:empty.jar"),
        ),
    },
    provides = [JavaInfo],
    implementation = _jvm_provided_library,
)
