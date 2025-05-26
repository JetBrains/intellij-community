load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")

visibility("private")

def _jvm_resources_impl(ctx):
    resultJar = ctx.actions.declare_file(ctx.label.name + ".jar")

    strip_prefix_dir = ctx.file.strip_prefix
    strip_prefix = ""
    if strip_prefix_dir == None:
        strip_prefix = "|" + ctx.label.package
    else:
        strip_prefix = strip_prefix_dir.path

    ctx.actions.run(
        mnemonic = "PackageResources",
        inputs = ctx.files.files,
        # avoid creating small files on disk – trick Bazel using this workaround
        arguments = ["--flagfile=|jar|" + resultJar.path + "|" + strip_prefix],
        #         arguments = [args],
        outputs = [resultJar],
        use_default_shell_env = True,
        executable = ctx.executable._worker,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
        },
        env = {
            # for Java source files
            "LC_CTYPE": "en_US.UTF-8",
        },
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
            providers = ["FileProvider"],
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to strip from Java resources""",
            allow_single_file = True,
            providers = ["FileProvider"],
        ),
        # see https://bazel.build/extending/rules#private_attributes_and_implicit_dependencies about implicit dependencies
        "_worker": attr.label(
            default = Label("//:resource-packager"),
            executable = True,
            allow_files = True,
            cfg = "exec",
        ),
    },
    provides = [JavaInfo],
)
