load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition", "scrubbed_host_platform_transition")

visibility("private")

def _jvm_resources_impl(ctx):
    resultJar = ctx.actions.declare_file(ctx.label.name + ".jar")

    strip_prefix_dir = ctx.file.strip_prefix
    strip_prefix = ""
    if strip_prefix_dir == None:
        strip_prefix = "|" + ctx.label.package
    else:
        strip_prefix = strip_prefix_dir.path

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]

    ctx.actions.run(
        mnemonic = "PackageResources",
        inputs = ctx.files.files,
        # avoid creating small files on disk – trick Bazel using this workaround
        arguments = ctx.attr._worker_jvm_flags[BuildSettingInfo].value + [
            ctx.file._worker_launcher.path,
            ctx.file._worker.path,
            "--flagfile=|jar|" + resultJar.path + "|" + ctx.attr.add_prefix + "|" + strip_prefix,
        ],
        #         arguments = [args],
        outputs = [resultJar],
        tools = [ctx.file._worker_launcher, ctx.file._worker],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        env = {
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
        ),
        "add_prefix": attr.string(
            doc = """The path prefix to prepend to Java resources, after applying `strip_prefix` (if any) to each file's relative path""",
            default = "",
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to remove from Java resources""",
            allow_single_file = True,
            providers = ["FileProvider"],
        ),
        # see https://bazel.build/extending/rules#private_attributes_and_implicit_dependencies about implicit dependencies
        "_worker": attr.label(
            default = "//:resource-packager",
            allow_single_file = True,
            cfg = scrubbed_host_platform_transition,
        ),
        "_worker_jvm_flags": attr.label(
            default = "//:resource-packager-jvm_flags",
        ),
        "_worker_launcher": attr.label(
            default = "//:rules/impl/MemoryLauncher.java",
            allow_single_file = True,
        ),
        "_tool_java_runtime": attr.label(
            default = "@bazel_tools//tools/jdk:current_java_runtime",
            cfg = "exec",
        ),
    },
    provides = [JavaInfo],
    cfg = jvm_platform_transition,
)

ResourceGroupInfo = provider(fields = ["files", "strip_prefix", "add_prefix"])

def _resourcegroup_impl(ctx):
    return [
        ResourceGroupInfo(files = ctx.files.srcs, strip_prefix = ctx.file.strip_prefix, add_prefix = ctx.attr.add_prefix),
    ]

resourcegroup = rule(
    doc = """This rule specifies resources layout in a .jar file.""",
    implementation = _resourcegroup_impl,
    attrs = {
        "srcs": attr.label_list(
            doc = """The list of resource files""",
            allow_files = True,
            mandatory = True,
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to remove from Java resources""",
            allow_single_file = True,
            providers = ["FileProvider"],
        ),
        "add_prefix": attr.string(
            doc = """The path prefix to prepend to Java resources, after applying `strip_prefix` (if any) to each file's relative path""",
            default = "",
        ),
    },
)
