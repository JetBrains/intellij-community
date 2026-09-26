"""The manifest that lays declared JPS project-model files out as a checkout-shaped tree.

A tool that loads the JPS project model wants a repository: `.idea/` at a known place, every `.iml` where
`modules.xml` says it is, and a marker file at the root. Bazel gives it an execroot instead, where an input
lives at its execpath and an external repository's files sit under `external/<repo>+/`.

Rather than bending either side, the action writes a manifest of `copy`/`create` rows and the tool
materializes the tree from it. Both readers of this format -
`org.jetbrains.intellij.build.dev.materializeProjectModelTree` and `JpsModuleToBazelTargetsOnly` - build the
tree the same way; this file is the single owner of the execpath-to-checkout-path mapping they both rely on.
"""

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

def project_model_manifest_lines(files, mode):
    """The manifest rows that reproduce [files] as a project tree.

    Args:
        files: the project-model `File`s to lay out, in any order.
        mode: "ultimate" or "community" - which repository root the tree is shaped as.

    Returns:
        A sorted list of tab-separated rows, ready for `ctx.actions.write`.
    """
    if mode not in ["ultimate", "community"]:
        fail("mode must be 'ultimate' or 'community', got '%s'" % mode)
    prefixes = _ULTIMATE_PREFIXES if mode == "ultimate" else _COMMUNITY_PREFIXES

    lines = []

    # A file can be named twice - as a project-model file and again as one of a product's extra files, the way
    # `ultimate-resources`' plugin.xml is both a module descriptor and the product's ApplicationInfo neighbour. The
    # materializer copies row by row and would fail on the second, so collapse the duplicates here and keep the
    # failure for the case that is genuinely wrong: two different sources claiming one destination.
    source_by_destination = {}
    for file in files:
        destination = file.path
        for p in prefixes:
            if file.path.startswith(p.path_prefix):
                destination = p.destination_prefix + file.path[len(p.path_prefix):]
                break

        existing = source_by_destination.get(destination)
        if existing == None:
            source_by_destination[destination] = file.path
            lines.append("copy\t%s\t%s" % (file.path, destination))
        elif existing != file.path:
            fail("Project model tree destination '%s' is claimed by both '%s' and '%s'" % (destination, existing, file.path))

    # The repository markers `IdeaProjectLoaderUtil` searches upwards for. Without them the tree is not
    # recognized as a repository at all, whatever `intellij.build.ultimate.home.path` is set to.
    if mode == "ultimate":
        lines.append("create\t\t.ultimate.root.marker")
        lines.append("create\t\tcommunity/.community.root.marker")
    else:
        lines.append("create\t\t.community.root.marker")

    return sorted(lines)

def write_project_model_manifest(ctx, name, files, mode):
    """Writes [project_model_manifest_lines] to a declared file and returns it."""
    manifest = ctx.actions.declare_file(name)
    ctx.actions.write(manifest, "\n".join(project_model_manifest_lines(files, mode)) + "\n")
    return manifest
