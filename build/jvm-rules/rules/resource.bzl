load("@rules_java//java:defs.bzl", "JavaInfo", "java_library")
load("//:rules/common-attrs.bzl", "USE_RULES_KOTLIN_BACKEND")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition")

visibility("private")

ResourceGroupInfo = provider(fields = ["files", "strip_prefix", "add_prefix"])

def _resourcegroup_jps_impl(ctx):
    return [
        ctx.attr.resource_jar[0][DefaultInfo],
        ctx.attr.resource_jar[0][JavaInfo],
        ctx.attr.resource_jar[0][OutputGroupInfo],
        ResourceGroupInfo(files = ctx.files.resources, strip_prefix = ctx.file.strip_prefix, add_prefix = ctx.attr.add_prefix),
    ]

_resourcegroup_jps = rule(
    doc = """This rule specifies resources layout in a .jar file.""",
    implementation = _resourcegroup_jps_impl,
    attrs = {
        "resources": attr.label_list(
            doc = """The list of resource files""",
            allow_files = True,
            mandatory = True,
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to remove from Java resources""",
            allow_single_file = True,
        ),
        "resource_strip_prefix": attr.string(
            doc = """\
            Equivalent of `strip_prefix`, but includes the package path as a prefix.
            Its name and value follow the standard Bazel convention and are properly recognized by the Bazel plugin.
            It's only added to satisfy the plugin. Monorepo rules use `strip_prefix` value instead.
            """,
        ),
        "add_prefix": attr.string(
            doc = """The path prefix to prepend to Java resources, after applying `strip_prefix` (if any) to each file's relative path""",
            default = "",
        ),
        "resource_jar": attr.label(
            doc = """The resource jar with the actual providers to support Bazel plugin.""",
            mandatory = True,
            cfg = jvm_platform_transition,
        ),
    },
)

def resourcegroup(name, srcs, strip_prefix, visibility = ["//visibility:private"]):
    package_name = native.package_name()
    if package_name:
        package_name = package_name + "/"
    if USE_RULES_KOTLIN_BACKEND:
        java_library(
            name = name,
            resources = srcs,
            resource_strip_prefix = package_name + strip_prefix,
            visibility = visibility,
        )
    else:
        # forward the actual providers for Java resources from java_library to support Bazel plugin
        java_library(
            name = name + "_lib",
            resources = srcs,
            resource_strip_prefix = package_name + strip_prefix,
            visibility = ["//visibility:private"],
            tags = ["manual"],
        )
        _resourcegroup_jps(
            name = name,
            resources = srcs,
            strip_prefix = strip_prefix,
            resource_strip_prefix = package_name + strip_prefix,
            resource_jar = name + "_lib",
            visibility = visibility,
        )
