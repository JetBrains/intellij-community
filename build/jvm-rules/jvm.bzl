load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")

def _jvm_resources_impl(ctx):
    resultJar = ctx.actions.declare_file(ctx.label.name + ".jar")

#     args = ctx.actions.args()
#     args.use_param_file("--flagfile=%s", use_always = True)
#     args.add("jar")
#     args.add(resultJar)

    ctx.actions.run(
        mnemonic = "PackageResources",
        inputs = ctx.files.files,
        # avoid creating small files on disk – trick Bazel using this workaround
        arguments = ["--flagfile=|jar|" + resultJar.path + "|" + ctx.file.strip_prefix.path],
#         arguments = [args],
        outputs = [resultJar],
        executable = ctx.executable._worker,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
        },
        env = {
          # for Java source files
          "LC_CTYPE": "en_US.UTF-8",
        }
    )
    return [
        DefaultInfo(
            files = depset([resultJar]),
        ),
        JavaInfo(
            output_jar = resultJar,
            # resource jar - should not be added as a compile-time dependency
            compile_jar = None,
        ),
    ]

jvm_resources = rule(
    doc = """This rule packages resources into a .jar file.""",
    implementation = _jvm_resources_impl,
    attrs = {
        "files": attr.label_list(
            doc = """The list of resource files to create the target""",
            allow_files = True,
            mandatory = True,
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to strip from Java resources""",
            allow_single_file = True,
        ),
        # see https://bazel.build/extending/rules#private_attributes_and_implicit_dependencies about implicit dependencies
        "_worker": attr.label(
            default = Label(":worker-native"),
#             default = Label(":worker-jvm"),
            executable = True,
            allow_files = True,
            cfg = "exec",
        ),
    },
    provides = [JavaInfo],
)
