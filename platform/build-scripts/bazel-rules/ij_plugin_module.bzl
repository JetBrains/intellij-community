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

def _ij_plugin_module_impl(ctx):
    module = ctx.attr.module
    kt_jvm_info = module[_KtJvmInfo]

    module_jar = _module_output_jar(module)
    if module_jar == None:
        fail("%s has a module name ('%s') but produced no output jar" % (module.label, kt_jvm_info.module_name), attr = "module")
    all_output_jars = [module_jar]
    for dep in ctx.attr.packed_deps:
        java_info = dep[JavaInfo]
        if hasattr(java_info, "output_jar"):  # from jvm_import rule
            all_output_jars.append(java_info.output_jar)
        elif hasattr(java_info, "transitive_runtime_jars"):  # from jvm_library rule for multi-JAR library
            for output in java_info.transitive_runtime_jars.to_list():
                all_output_jars.append(output)
        else:
            fail(
                "JavaInfo has neither output_jar not transitive_runtime_jars fields; available fields: %s" %
                ", ".join(dir(java_info)),
            )

    return [
        module[DefaultInfo],
        PluginModuleInfo(
            module_name = kt_jvm_info.module_name,
            all_output_jars = all_output_jars,
            non_classpath_data = _collect_non_classpath_data(ctx.attr.non_classpath_data),
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
    },
    provides = [PluginModuleInfo],
)

def ij_plugin_module(
        name,
        module_name,
        non_classpath_data = [],
        packed_deps = [],
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
        visibility: Target visibility
        **kwargs: Additional arguments passed to jvm_library macro
    """

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
        tags = kwargs.get("tags", []),
        testonly = kwargs.get("testonly", False),
        visibility = visibility,
    )
