"""A source tree of many Bazel packages as one declared test input.

A `glob` stops at the boundary of each package. So each package of a tree calls `source_tree_files()`, which globs its
own files and adds the `source_tree_files` target of each of its subpackages. A subpackage without the call fails the
analysis of the tree, so no package drops out of the tree silently.

`source_tree` writes `TREE_FILES`, the sorted list of the files, and gives the files as runfiles. A test reads only the
files that `TREE_FILES` lists, so a test needs no `git`, no checkout and no directory walk. This works also with the
runfiles manifest of Windows.
"""

# The files under each root of a tree that no test reads: a file under a hidden directory or under `node_modules`, and
# the local outputs `.DS_Store` and `*.tsbuildinfo`. A pattern for a directory ends with `/**/*`: a final `**` also
# matches no segment, so `.*/**` would match a file such as `.gitignore` too.
SOURCE_TREE_EXCLUDES = [
    "**/.*/**/*",
    "**/.DS_Store",
    "**/node_modules/**/*",
    "**/*.tsbuildinfo",
]

TREE_FILES_NAME = "TREE_FILES"

_COMMUNITY_REPOSITORY = Label("//:BUILD.bazel").repo_name

def _monorepo_directory(repo_name, path):
    """The checkout path of `path`, a path in the repository `repo_name`. The community module is under `community/`."""
    if repo_name == "":
        return path
    if repo_name == _COMMUNITY_REPOSITORY:
        return "community/" + path if path else "community"
    fail("the repository %s has no place in the monorepo checkout" % repo_name)

def source_tree_files(name = "source_tree_files", roots = [""]):
    """The files under `roots` of this package and the files of each subpackage there, for a `source_tree`.

    Each package of a tree calls it, so a subpackage without the call fails the analysis of the tree.

    Args:
      name: the name of the filegroup. A subpackage must use the same name.
      roots: the directories of the package that the tree holds, relative to the package. The default is the whole
        package. A package that holds a tree only in part, such as the root package, names the directories.
    """
    include = []
    exclude = []
    for root in roots:
        prefix = root.rstrip("/") + "/" if root else ""
        include.append(prefix + "**")
        exclude.extend([prefix + pattern for pattern in SOURCE_TREE_EXCLUDES])
    native.filegroup(
        name = name,
        srcs = native.glob(include, exclude = exclude, allow_empty = True) + [
            "//%s:%s" % (native.package_name() + "/" + subpackage if native.package_name() else subpackage, name)
            for subpackage in native.subpackages(include = include, allow_empty = True)
        ],
        visibility = ["//visibility:public"],
    )

def _monorepo_path(file):
    """The path of `file` in the monorepo checkout."""
    if file.short_path.startswith("../"):
        return _monorepo_directory(file.owner.repo_name, file.short_path.split("/", 2)[2])
    return _monorepo_directory(file.owner.repo_name, file.short_path)

def _rlocation_path(ctx, file):
    """The runfiles path of `file`, which a runfiles library resolves."""
    if file.short_path.startswith("../"):
        return file.short_path[len("../"):]
    return ctx.workspace_name + "/" + file.short_path

def _source_tree_impl(ctx):
    files = depset(transitive = [target[DefaultInfo].files for target in ctx.attr.srcs]).to_list()
    if not files:
        fail("the source tree %s is empty" % ctx.label)
    entries = {}
    for file in files:
        if not file.is_source:
            fail("%s is not a source file; a source tree holds only files of the checkout" % file.short_path)
        path = _monorepo_path(file)
        if path in entries:
            fail("%s is twice in the source tree %s" % (path, ctx.label))
        entries[path] = _rlocation_path(ctx, file)
    for root in ctx.attr.roots:
        prefix = root.rstrip("/") + "/"
        if not [path for path in entries if path.startswith(prefix)]:
            fail("the source tree %s has no file under the root %s" % (ctx.label, root))

    listing = ctx.actions.declare_file(ctx.label.name + "/" + TREE_FILES_NAME)
    ctx.actions.write(listing, "".join(["%s\t%s\n" % (path, entries[path]) for path in sorted(entries)]))
    return [
        DefaultInfo(
            files = depset([listing]),
            runfiles = ctx.runfiles(files = [listing], transitive_files = depset(files)),
        ),
    ]

source_tree = rule(
    doc = """The files of `srcs` as runfiles, and `<name>/TREE_FILES`, which lists them.

Each line of `TREE_FILES` holds the path of a file in the monorepo checkout, a tab and its runfiles path. The lines are
sorted by the checkout path. A test resolves the listing through its runfiles and reads only the listed files.
""",
    implementation = _source_tree_impl,
    attrs = {
        "roots": attr.string_list(
            doc = "The checkout directories that the tree must hold a file under. An empty root fails the build.",
        ),
        "srcs": attr.label_list(
            allow_files = True,
            doc = "The `source_tree_files` targets of the trees, and single files.",
        ),
    },
)
