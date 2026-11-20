load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_jvm//:jvm.bzl", _jvm_library = "jvm_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("@rules_pkg//pkg:providers.bzl", "PackageFilegroupInfo", "PackageFilesInfo")
load(":content_module_jar.bzl", _module_output_jar = "module_output_jar")

PluginModuleInfo = provider(
    fields = {
        "module_name": "The module name (from the `<module>` tag for a content module, or the JPS module name for a plugin descriptor module).",
        "all_output_jars": "All JARs that should be packed together with the module inside the plugin distribution",
        "non_classpath_data": "Files and directories to be copied to the plugin distribution; consists of structs with `file`, `relative_path` and `mode` properties",
        "packed_jar_entry_renamings": "Entries of `packed_deps` JARs to rename or skip; consists of structs with `jars`, `renamed` (a dict of the source path to the new path) and `skipped` (a list of source paths) properties",
    },
)

# The mode of a file that a `pkg_files` target states no mode for.
_DEFAULT_MODE = "0644"

def _collect_non_classpath_data(non_classpath_data):
    result = []
    for target in non_classpath_data:
        if PackageFilegroupInfo in target:
            group = target[PackageFilegroupInfo]
            if group.pkg_dirs or group.pkg_symlinks:
                fail("non_classpath_data target %s contains unsupported pkg_mkdirs or pkg_mklink mappings" % target.label)
            files_infos = group.pkg_files
        else:
            files_infos = [(target[PackageFilesInfo], target.label)]
        for files_info, origin in files_infos:
            mode = _DEFAULT_MODE
            for attribute, value in files_info.attributes.items():
                if attribute != "mode":
                    fail(
                        "non_classpath_data target %s specifies unsupported attribute '%s' = %r" %
                        (origin, attribute, value),
                    )
                mode = _normalize_mode(value, origin)
            for relative_path, file in files_info.dest_src_map.items():
                result.append(struct(file = file, relative_path = relative_path or ".", mode = mode))
    return result

def _normalize_mode(value, origin):
    """Returns `value` as four octal digits, or fails for a value that is not a permission mode of a file."""
    if type(value) != "string" or len(value) not in [3, 4] or not all([character in "01234567" for character in value.elems()]):
        fail("non_classpath_data target %s specifies unsupported mode %r: expected 3 or 4 octal digits" % (origin, value))
    if len(value) == 4 and value[0] != "0":
        fail("non_classpath_data target %s specifies unsupported mode %r: the setuid, setgid and sticky bits are not supported" % (origin, value))
    return value if len(value) == 4 else "0" + value

# The value in `packed_deps_entry_renaming` that skips an entry.
_SKIP_ENTRY = "$skip"

def _dep_jars(dep):
    """Returns the JARs of a `packed_deps` target."""
    java_info = dep[JavaInfo]
    if hasattr(java_info, "output_jar"):  # from jvm_import rule
        return [java_info.output_jar]
    if hasattr(java_info, "transitive_runtime_jars"):  # from jvm_library rule for multi-JAR library
        return java_info.transitive_runtime_jars.to_list()
    fail(
        "JavaInfo has neither output_jar not transitive_runtime_jars fields; available fields: %s" %
        ", ".join(dir(java_info)),
    )

# The separator between the JAR file name and the entry path in a key of `packed_deps_entry_renaming`.
_JAR_SEPARATOR = "!/"

def _collect_entry_renamings(packed_deps_entry_renaming, jars_by_label):
    result = []
    for target, encoded_renaming in packed_deps_entry_renaming.items():
        label = target.label
        jars = jars_by_label.get(label)
        if jars == None:
            fail("packed_deps_entry_renaming specifies %s, which is not in packed_deps" % label, attr = "packed_deps_entry_renaming")

        # the path of the selected JAR, or "" for all JARs of the label -> the rules for these JARs
        groups = {}
        for key, target_path in json.decode(encoded_renaming).items():
            source_path = key
            selected_jars = jars
            group_key = ""
            if _JAR_SEPARATOR in key:
                jar_name, source_path = key.split(_JAR_SEPARATOR, 1)
                jar = _select_jar(jar_name, jars, label)
                selected_jars = [jar]
                group_key = jar.path
            _check_entry_path(source_path, label)
            group = groups.get(group_key)
            if group == None:
                group = struct(jars = selected_jars, renamed = {}, skipped = [])
                groups[group_key] = group
            if target_path == _SKIP_ENTRY:
                group.skipped.append(source_path)
                continue
            _check_entry_path(target_path, label)
            if target_path == source_path:
                fail("packed_deps_entry_renaming of %s renames '%s' to the same path" % (label, key), attr = "packed_deps_entry_renaming")
            group.renamed[source_path] = target_path
        result.extend(groups.values())
    return result

def _select_jar(jar_name, jars, label):
    """Returns the JAR of `label` whose file name, with or without the version, is `jar_name`."""
    matching = [jar for jar in jars if jar_name in (jar.basename, _remove_version(jar.basename))]
    if len(matching) != 1:
        fail(
            "packed_deps_entry_renaming of %s specifies the JAR '%s', which matches %s JARs of the label: %s" %
            (label, jar_name, len(matching), ", ".join([jar.basename for jar in jars])),
            attr = "packed_deps_entry_renaming",
        )
    return matching[0]

def _remove_version(file_name):
    """Removes the version from a JAR file name, for example `asm-tree-9.10.1.jar` gives `asm-tree.jar`.

    The rule is the same as in `removeVersionFromJar` in `IjPluginPackager.kt`.
    """
    if not file_name.endswith(".jar"):
        return file_name
    parts = file_name[:-len(".jar")].rsplit("-", 1)
    if len(parts) == 2 and all([part.isdigit() for part in parts[1].split(".")]):
        return parts[0] + ".jar"
    return file_name

def _check_entry_path(path, label):
    """Fails for a path that cannot be a JAR entry or that contains a separator of the packager arguments."""
    if type(path) != "string" or not path or path.startswith("/") or path.endswith("/") or any([c in path for c in [":", ",", "\n"]]):
        fail(
            "packed_deps_entry_renaming of %s specifies unsupported entry path %r: expected a relative file path without ':', ',' and line breaks" %
            (label, path),
            attr = "packed_deps_entry_renaming",
        )

def _ij_plugin_module_impl(ctx):
    module = ctx.attr.module
    kt_jvm_info = module[_KtJvmInfo]

    module_jar = _module_output_jar(module)
    if module_jar == None:
        fail("%s has a module name ('%s') but produced no output jar" % (module.label, kt_jvm_info.module_name), attr = "module")
    all_output_jars = [module_jar]
    jars_by_label = {}
    for dep in ctx.attr.packed_deps:
        jars = _dep_jars(dep)
        all_output_jars.extend(jars)
        jars_by_label[dep.label] = jars

    return [
        module[DefaultInfo],
        PluginModuleInfo(
            module_name = kt_jvm_info.module_name,
            all_output_jars = all_output_jars,
            non_classpath_data = _collect_non_classpath_data(ctx.attr.non_classpath_data),
            packed_jar_entry_renamings = _collect_entry_renamings(ctx.attr.packed_deps_entry_renaming, jars_by_label),
        ),
    ]

_ij_plugin_module = rule(
    implementation = _ij_plugin_module_impl,
    attrs = {
        "module": attr.label(
            mandatory = True,
            providers = [[JavaInfo, _KtJvmInfo]],
        ),
        "non_classpath_data": attr.label_list(
            providers = [[PackageFilesInfo], [PackageFilegroupInfo]],
            doc = """A list of `pkg_files` or `pkg_filegroup` targets from `@rules_pkg`.
            The mappings specify relative output paths in the plugin distribution.
            Data for content modules goes under the `modules/<module.name>` directory in the plugin distribution.
            Groups must contain only file mappings; `pkg_mkdirs` and `pkg_mklink` are not supported.
            The `attributes` of a `pkg_files` target can state only `mode`, as 3 or 4 octal digits without the setuid, setgid and sticky
            bits. The mode applies to each regular file that the target maps, also to each file of a mapped directory. The default mode is
            `0644`. The `ij_plugin` rule writes each mode other than `0644` to `file-attributes.txt`.
            Symbolic links pointing to files under the same directory are copied as symbolic links inside the distribution.
            """,
        ),
        "packed_deps": attr.label_list(
            providers = [JavaInfo],
            doc = """Dependencies that are packed together with this module in the plugin distribution.

            For a content module, the packager merges these JARs into the content module JAR.
            For a plugin descriptor module, each JAR becomes a separate JAR in the `lib` directory, named after the JAR file
            with the version removed.
            """,
        ),
        "packed_deps_entry_renaming": attr.label_keyed_string_dict(
            doc = """Entries of the `packed_deps` JARs that the packager renames or skips.

            Each key is a label from `packed_deps`. Each value is a JSON object that maps the path of an entry in the JARs of that label
            to the new path of the entry, or to `$skip`. The packager does not pack an entry that maps to `$skip`.
            The packager reports an error if an entry is in none of the JARs of its label.

            An entry path applies to all JARs of the label. To select one JAR, write `<jar-file-name>!/<entry-path>`.
            The JAR file name is the name with the version, for example `asm-tree-9.10.1.jar`, or without it, for example `asm-tree.jar`.
            The rule reports an error if the name matches no JAR of the label, or more than one.
            Use the `ij_plugin_module` macro, which writes the JSON objects from dicts.
            """,
        ),
    },
    provides = [PluginModuleInfo],
)

def ij_plugin_module(
        name,
        module_name,
        non_classpath_data = [],
        packed_deps = [],
        packed_deps_entry_renaming = {},
        visibility = None,
        **kwargs):
    """Defines a plugin module that can be included in ij_plugin.
    It can be either a content module or a plugin descriptor module.
    The macro delegates compilation to jvm_library and adds plugin packaging data to its output.

    Args:
        name: Target name
        module_name: Name of the content module or JPS module for the plugin descriptor module
        non_classpath_data: `pkg_files` or `pkg_filegroup` targets that map files and directories to paths in the distribution.
            A `pkg_files` target can state the mode of its files with `pkg_attributes(mode = ...)`.
        packed_deps: Dependencies that should be packed together with this module in the plugin distribution
        packed_deps_entry_renaming: A dict that maps a label from `packed_deps` to a dict of entry paths in its JARs.
            The inner dict maps the path of an entry to its new path, or to `$skip` to leave the entry out.
            The path of an entry applies to all JARs of the label. `<jar-file-name>!/<entry-path>` selects one JAR,
            by the file name with or without the version.
            Use it when two JARs that the packager merges into one JAR have an entry with the same path.
        visibility: Target visibility
        **kwargs: Additional arguments passed to jvm_library macro
    """

    encoded_entry_renaming = {}
    for label, renaming in packed_deps_entry_renaming.items():
        if type(renaming) != "dict":
            fail("packed_deps_entry_renaming of %s must map each label to a dict, but it maps %s to %r" % (name, label, renaming))
        encoded_entry_renaming[label] = json.encode(renaming)

    _jvm_library(
        name = name,
        module_name = module_name,
        visibility = visibility,
        **kwargs
    )
    _ij_plugin_module(
        name = name + "_plugin_module",
        module = name,
        non_classpath_data = non_classpath_data,
        packed_deps = packed_deps,
        packed_deps_entry_renaming = encoded_entry_renaming,
        tags = kwargs.get("tags", []),
        testonly = kwargs.get("testonly", False),
        visibility = visibility,
    )
