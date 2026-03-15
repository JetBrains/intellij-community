visibility("private")

ResourceGroupInfo = provider(fields = ["files", "strip_prefix", "add_prefix"])

def _strip_known_prefix(path, prefix):
    if not prefix:
        return path
    normalized_prefix = prefix[:-1] if prefix.endswith("/") else prefix
    if path.startswith(normalized_prefix + "/"):
        return path[len(normalized_prefix) + 1:]
    return path

def _to_resource_jar_path(path, strip_prefix, add_prefix):
    stripped = _strip_known_prefix(path, strip_prefix)
    if not add_prefix:
        return stripped
    if not stripped:
        return add_prefix
    return add_prefix + "/" + stripped

def _resourcegroup_impl(ctx):
    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    strip_prefix = ctx.file.strip_prefix.short_path if ctx.file.strip_prefix else ""

    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("@%s", use_always = True)
    args.add("--output")
    args.add(output_jar.path)
    for resource in sorted(ctx.files.srcs, key = lambda f: f.short_path):
        args.add("--entry")
        args.add(_to_resource_jar_path(resource.short_path, strip_prefix, ctx.attr.add_prefix))
        args.add(resource.path)

    ctx.actions.run(
        executable = ctx.executable._resource_jar_builder,
        arguments = [args],
        inputs = ctx.files.srcs,
        outputs = [output_jar],
        tools = [ctx.executable._resource_jar_builder],
        mnemonic = "JvmResourceJar",
        progress_message = "Create resource jar %{label}",
    )

    return [
        DefaultInfo(files = depset([output_jar])),
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
        ),
        "add_prefix": attr.string(
            doc = """The path prefix to prepend to Java resources, after applying `strip_prefix` (if any) to each file's relative path""",
            default = "",
        ),
        "_resource_jar_builder": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//:resource-jar-builder"),
            allow_files = True,
        ),
    },
)
