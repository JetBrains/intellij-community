visibility("private")

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
