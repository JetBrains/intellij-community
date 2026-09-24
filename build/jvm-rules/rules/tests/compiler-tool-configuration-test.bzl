load("//:jvm.bzl", "jvm_library")

visibility("private")

_CompilerToolInfo = provider(fields = ["jar"])

def _compiler_tool_aspect_impl(_target, ctx):
    return [_CompilerToolInfo(jar = ctx.rule.files._jvm_builder[0])]

_compiler_tool_aspect = aspect(implementation = _compiler_tool_aspect_impl)

def _compiler_tool_configuration_test_impl(ctx):
    target_jar = ctx.attr.target_library[_CompilerToolInfo].jar
    exec_jar = ctx.attr.exec_library[_CompilerToolInfo].jar
    return [AnalysisTestResultInfo(
        success = target_jar == exec_jar,
        message = "The compiler JAR must be shared by target and exec configurations: %s != %s" % (target_jar.path, exec_jar.path),
    )]

_compiler_tool_configuration_test = rule(
    implementation = _compiler_tool_configuration_test_impl,
    analysis_test = True,
    attrs = {
        "target_library": attr.label(aspects = [_compiler_tool_aspect]),
        "exec_library": attr.label(aspects = [_compiler_tool_aspect], cfg = "exec"),
    },
)

def compiler_tool_configuration_test(name):
    jvm_library(
        name = name + "_library",
        srcs = [],
        testonly = True,
        tags = ["manual"],
        use_rules_kotlin_backend = False,
    )
    _compiler_tool_configuration_test(
        name = name,
        size = "small",
        target_library = ":" + name + "_library",
        exec_library = ":" + name + "_library",
    )
