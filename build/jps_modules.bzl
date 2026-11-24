"""The `jps_modules` repository rule: the groups of the JPS modules of one repository half.

The rule reads the module list from `.idea/modules.xml`. The package of a module is the directory of its `.iml` file,
and the record of the module is the `jps_module` call in that package, see `jps_module.bzl`. The rule reads no `.iml`
file. It runs again when `modules.xml`, a `bazel-generated-file-list.txt`, a `dev-build.json` or the
`JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT` environment variable changes.
"""

def _module_paths(modules_xml_text, errors):
    """The project-relative path of each `.iml` file that `modules.xml` names, in the order of the file."""
    marker = 'filepath="'
    prefix = "$PROJECT_DIR$/"

    paths = []
    i = 0

    # Upper bound: you can't have more attributes than characters.
    for _ in range(len(modules_xml_text) + 1):
        j = modules_xml_text.find(marker, i)
        if j == -1:
            break

        j += len(marker)
        k = modules_xml_text.find('"', j)
        if k == -1:
            errors.append("Malformed modules.xml: an unterminated filepath attribute")
            break

        full = modules_xml_text[j:k]
        if full.startswith(prefix):
            paths.append(full[len(prefix):])
        else:
            errors.append("A module filepath of modules.xml does not start with %r: %r" % (prefix, full))
        i = k + 1

    if not paths:
        errors.append("modules.xml names no module")

    return paths

def _package_label(layout, directory):
    """The label of the package of the project-relative `directory`."""
    for prefix, repository in layout.repositories.items():
        if directory == prefix.rstrip("/"):
            return repository
        if directory.startswith(prefix):
            return repository + directory[len(prefix):]
    return layout.repository + directory

def jps_modules_layout(modules_xml, generated_file_lists, dev_build_files, layout):
    """The records, the `.iml` files without a record, the build modules and the problems of one half.

    The function reads only text, so a unit test can call it.

    Args:
        modules_xml: The text of `.idea/modules.xml`.
        generated_file_lists: The text of each `bazel-generated-file-list.txt`, by the prefix of its directories. A line
            holds a directory and the modules that the converter wrote there, separated by spaces.
        dev_build_files: The name and the text of each `dev-build.json`, as pairs.
        layout: A struct with the attributes `repository`, `repositories`, `packages`, `imls` and `build_modules` of the
            rule.

    Returns:
        A struct with `records`, `imls`, `build_modules` and `errors`. A record and a build module are
        `<package>:<module>`. An error is a message for the analysis of the groups.
    """
    errors = []

    # A list leaves out the root of its half, whose BUILD file the converter only updates.
    roots = {"": None}
    written = {}
    for prefix, text in generated_file_lists.items():
        roots[prefix.rstrip("/")] = None
        for line in text.splitlines():
            parts = [part for part in line.strip().split(" ") if part]
            if parts:
                written[prefix + parts[0]] = {name: None for name in parts[1:]}

    records = []
    imls = []
    missing = []
    for path in _module_paths(modules_xml, errors):
        directory, _, file_name = path.rpartition("/")
        if not file_name.endswith(".iml"):
            errors.append("A module file of modules.xml is not an .iml file: %s" % path)
            continue
        name = file_name[:-len(".iml")]
        if name in layout.imls:
            imls.append(layout.imls[name])
            continue

        directory = layout.packages.get(name, directory)

        # The converter lists each module whose record it wrote, on the line of the directory. A new or a renamed module
        # has no record until the converter runs.
        if directory not in roots and name not in written.get(directory, {}):
            missing.append("%s (%s)" % (name, path))
            continue
        records.append("%s:%s" % (_package_label(layout, directory), name))

    if missing:
        errors.append("%d module(s) of modules.xml have no record. Run ./build/jpsModelToBazel.cmd.\n  %s" % (
            len(missing),
            "\n  ".join(missing),
        ))

    build_modules = {name: None for name in layout.build_modules}
    for file_name, text in dev_build_files:
        data = json.decode(text, default = None)
        if type(data) != "dict":
            errors.append("%s is not a JSON object" % file_name)
            continue
        for product in data.get("products", {}).values():
            build_modules.update({name: None for name in product.get("modules", [])})
    record_by_module = {record.rpartition(":")[2]: record for record in records}
    unknown = sorted([name for name in build_modules if name not in record_by_module])
    if unknown:
        errors.append("The build modules of %s are stale. The project model does not have them:\n  %s" % (
            ", ".join([file_name for file_name, _ in dev_build_files]),
            "\n  ".join(unknown),
        ))

    return struct(
        records = sorted(records),
        imls = sorted(imls),
        build_modules = sorted([record_by_module[name] for name in build_modules if name in record_by_module]),
        errors = errors,
    )

def _jps_modules_impl(rctx):
    attr = rctx.attr
    layout = jps_modules_layout(
        modules_xml = rctx.read(rctx.path(attr.modules_xml), watch = "yes"),
        generated_file_lists = {prefix: rctx.read(rctx.path(file), watch = "yes") for file, prefix in attr.generated_file_lists.items()},
        dev_build_files = [(str(file), rctx.read(rctx.path(file), watch = "yes")) for file in attr.build_module_files],
        layout = struct(
            repository = attr.repository,
            repositories = attr.repositories,
            packages = attr.packages,
            imls = attr.imls,
            build_modules = attr.build_modules,
        ),
    )

    # The rule does not fail the fetch, because a failed fetch fails the load of each package that loads `defs.bzl`.
    # The problems fail the analysis of the groups. Bazel runs the rule again when one of its inputs changes.
    rctx.file("BUILD.bazel", _BUILD_TEMPLATE.format(
        jps_module_bzl = str(Label("//build:jps_module.bzl")),
        records = _starlark_list(layout.records),
        imls = _starlark_list(layout.imls),
        build_modules = _starlark_list(layout.build_modules),
        errors = _starlark_list(layout.errors),
    ))
    rctx.file("defs.bzl", _DEFS_TEMPLATE.format(
        kotlin_dev_version_as_snapshot = repr(rctx.getenv("JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT") or ""),
    ))

def _starlark_list(values):
    if not values:
        return "[]"
    return "[\n" + "".join(["    %s,\n" % repr(value) for value in values]) + "]"

_BUILD_TEMPLATE = """\
# Generated by the `jps_modules` repository rule from `.idea/modules.xml`. Do not edit.

load("{jps_module_bzl}", "jps_modules_failure")

# The record of each module: the package of its `.iml` file and the module name.
_RECORDS = {records}

# The `.iml` file of each module without a record.
_IMLS = {imls}

# The build modules of the products, whose closure the plugin model tool loads.
_BUILD_MODULES = {build_modules}

# The problems of the module list. Each group below fails its analysis with them.
_ERRORS = {errors}

jps_modules_failure(
    name = "failure",
    messages = _ERRORS,
)

_FAILURE = [":failure"] if _ERRORS else []

# The `.iml` file and the descriptors of each module, source files only.
filegroup(
    name = "model_files",
    srcs = [record + "_model_files" for record in _RECORDS] + _IMLS + _FAILURE,
    visibility = ["//visibility:public"],
)

# The jar of each module and the jars of the local module libraries.
filegroup(
    name = "jars",
    srcs = [":production_jars"] + [record + "_test_jars" for record in _RECORDS],
    visibility = ["//visibility:public"],
)

# The production jars and the jars of the local module libraries. They do not reach a test target.
filegroup(
    name = "production_jars",
    srcs = [record + "_jars" for record in _RECORDS] + _FAILURE,
    visibility = ["//visibility:public"],
)

# The resource jar of each module with resources. A resource jar of a test module is testonly.
filegroup(
    name = "resource_jars",
    testonly = True,
    srcs = [record + "_resource_jars" for record in _RECORDS] + _FAILURE,
    visibility = ["//visibility:public"],
)

# The jars of the build modules. A rule that walks the module graph from them reaches the closure.
filegroup(
    name = "build_module_jars",
    srcs = [record + "_jars" for record in _BUILD_MODULES] + _FAILURE,
    visibility = ["//visibility:public"],
)

# The `ij_plugin` target of each module that has one.
filegroup(
    name = "plugin_distributions",
    srcs = [record + "_plugin_distribution" for record in _RECORDS] + _FAILURE,
    visibility = ["//visibility:public"],
)
"""

_DEFS_TEMPLATE = """\
# Generated by the `jps_modules` repository rule. Do not edit.

# The `JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT` of the Bazel client. The Kotlin cooperative build sets it.
JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT = {kotlin_dev_version_as_snapshot}
"""

jps_modules = repository_rule(
    doc = """The groups of the JPS modules of one repository half.

The repository has the targets `model_files`, `jars`, `production_jars`, `resource_jars`, `build_module_jars` and
`plugin_distributions`, and `defs.bzl` with `JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT`. A module of
`modules.xml` without a record, or a stale build module, fails the analysis of each group with a message that names
the module and `./build/jpsModelToBazel.cmd`. The fetch does not fail, so `defs.bzl` always loads.
""",
    implementation = _jps_modules_impl,
    attrs = {
        "modules_xml": attr.label(
            doc = "The `.idea/modules.xml` of the half.",
            mandatory = True,
        ),
        "repository": attr.string(
            doc = "The repository of a package of the half, such as `@//` or `@community//`.",
            mandatory = True,
        ),
        "repositories": attr.string_dict(
            doc = "The repository of each project-relative directory prefix that is not on the half, such as `community/`.",
        ),
        "packages": attr.string_dict(
            doc = "The project-relative package of each module whose package is not the directory of its `.iml` file. " +
                  "It mirrors `DEFAULT_CUSTOM_MODULES` of the converter.",
        ),
        "build_module_files": attr.label_list(
            doc = "The `dev-build.json` files that name the build modules of the products.",
        ),
        "build_modules": attr.string_list(
            doc = "The build modules that every product needs in addition, see `getBuildModules` in `IdeBuilder.kt`.",
        ),
        "generated_file_lists": attr.label_keyed_string_dict(
            doc = "The `bazel-generated-file-list.txt` of each half with the prefix of its directories. " +
                  "A module that the line of its directory does not name has no record.",
        ),
        "imls": attr.string_dict(
            doc = "The `.iml` label of each module that has no record, because it is a separate Bazel project. " +
                  "It mirrors the skipped modules of the converter.",
        ),
    },
)
