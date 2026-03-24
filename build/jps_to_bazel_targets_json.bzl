_ULTIMATE_PREFIXES = [
    struct(
        path_prefix = "external/community+/",
        destination_prefix = "community/",
    ),
    struct(
        path_prefix = "external/jps_to_bazel+/",
        destination_prefix = "community/platform/build-scripts/bazel/",
    ),
]

_COMMUNITY_PREFIXES = [
    struct(
        path_prefix = "external/community+/",
        destination_prefix = "",
    ),
    struct(
        path_prefix = "external/jps_to_bazel+/",
        destination_prefix = "platform/build-scripts/bazel/",
    ),
]

def _add_file_mappings(lines, files, prefixes):
    for file in files:
        destination = file.path
        for p in prefixes:
            if file.path.startswith(p.path_prefix):
                destination = p.destination_prefix + file.path[len(p.path_prefix):]
                break
        lines.append("copy\t%s\t%s" % (file.path, destination))

def _jps_to_bazel_targets_json_impl(ctx):
    output = ctx.actions.declare_file("bazel-targets.json")
    manifest = ctx.actions.declare_file(ctx.label.name + ".manifest")

    mode = ctx.attr.mode
    prefixes = _ULTIMATE_PREFIXES if mode == "ultimate" else _COMMUNITY_PREFIXES

    manifest_lines = []
    _add_file_mappings(manifest_lines, ctx.files.srcs, prefixes)
    if mode == "ultimate":
        manifest_lines.append("create\t\t.ultimate.root.marker")
        manifest_lines.append("create\t\tcommunity/.community.root.marker")
    else:
        manifest_lines.append("create\t\t.community.root.marker")

    ctx.actions.write(manifest, "\n".join(sorted(manifest_lines)) + "\n")

    args = ctx.actions.args()
    args.add("--manifest=" + manifest.path)
    args.add("--output=" + output.path)
    args.add_all(ctx.attr.starlark_production_targets, format_each = "--starlark-production=%s")
    args.add_all(ctx.attr.starlark_test_targets, format_each = "--starlark-test=%s")
    args.add_all(ctx.attr.starlark_library_targets, format_each = "--starlark-library=%s")
    args.add_all(ctx.attr.starlark_iml_targets, format_each = "--starlark-iml=%s")
    args.use_param_file("@%s", use_always = True)

    ctx.actions.run(
        inputs = ctx.files.srcs + [manifest],
        outputs = [output],
        executable = ctx.executable.tool,
        arguments = [args],
        mnemonic = "JpsToBazelTargetsJson",
        progress_message = "Generating bazel-targets.json for %s" % ctx.label,
    )

    return DefaultInfo(files = depset([output]))

jps_to_bazel_targets_json = rule(
    implementation = _jps_to_bazel_targets_json_impl,
    attrs = {
        "mode": attr.string(
            mandatory = True,
            values = ["community", "ultimate"],
            doc = "Build mode: 'community' for community-only, 'ultimate' for full project.",
        ),
        "srcs": attr.label_list(
            allow_files = True,
            doc = "JPS project model files",
        ),
        "tool": attr.label(
            executable = True,
            cfg = "exec",
            allow_files = False,
            mandatory = True,
            doc = "Hermetic bazel-targets.json generator executable.",
        ),
        "starlark_production_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived production targets for parity assertion.",
        ),
        "starlark_test_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived test targets for parity assertion.",
        ),
        "starlark_library_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived library targets for parity assertion.",
        ),
        "starlark_iml_targets": attr.string_list(
            default = [],
            doc = "Starlark-derived IML targets for parity assertion.",
        ),
    },
)
