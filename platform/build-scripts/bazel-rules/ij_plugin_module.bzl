load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_jvm//:jvm.bzl", _jvm_library = "jvm_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

PluginModuleInfo = provider(
    fields = {
        "module_name": "The module name (from the `<module>` tag for a content module, or the JPS module name for a plugin descriptor module).",
        "all_output_jars": "All JARs that should be packed together with the module inside the plugin distribution",
        "non_classpath_data": "Files and directories to be copied to the plugin distribution; consists of structs with `file` and `relative_path` properties",
    },
)

def _common_parent(files):
    common_parent = files[0].short_path.split("/")[:-1]
    for file in files[1:]:
        components = file.short_path.split("/")[:-1]
        common_length = len(common_parent)
        if len(components) < common_length:
            common_length = len(components)
        for index in range(common_length):
            if common_parent[index] != components[index]:
                common_length = index
                break
        common_parent = common_parent[:common_length]
    return "/".join(common_parent)

def _join_path(parent, child):
    if not parent or parent.endswith("/"):
        return parent + child
    return parent + "/" + child

def _collect_non_classpath_data(non_classpath_data):
    result = []
    for target, relative_path in non_classpath_data.items():
        files = target[DefaultInfo].files.to_list()
        if not files:
            fail("non_classpath_data key %s provides no files or directories" % target.label)
        if len(files) == 1:
            file = files[0]
            if relative_path.endswith("/"):
                relative_path += file.basename
            result.append(struct(file = file, relative_path = relative_path))
            continue

        if not relative_path.endswith("/"):
            fail("non_classpath_data key %s provides multiple files or directories, but its output path '%s' does not end with '/'" % (target.label, relative_path))

        common_parent = _common_parent(files)
        prefix_length = len(common_parent) + 1 if common_parent else 0
        for file in files:
            result.append(struct(
                file = file,
                relative_path = _join_path(relative_path, file.short_path[prefix_length:]),
            ))
    return result

def _ij_plugin_module_impl(ctx):
    module = ctx.attr.module
    kt_jvm_info = module[_KtJvmInfo]

    all_output_jars = []
    for jar in kt_jvm_info.all_output_jars:
        all_output_jars.append(jar)
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
        "non_classpath_data": attr.label_keyed_string_dict(
            allow_files = True,
            doc = """A map from a target to a relative output path in the plugin distribution.
            If the path ends with `/`, the target files are copied to the directory with that path.
            Otherwise, the target must produce a single file or directory that is copied to the specified path.
            If the target produces multiple files, they are placed under the output path accordingly to their relative paths from the common
            parent.
            Data for content modules is put under `modules/<module.name>` directory in the plugin distribution.
            Symbolic links pointing to files under the same directory are copied as symbolic links inside the distribution.
            """,
        ),
        "packed_deps": attr.label_list(
            providers = [JavaInfo],
        ),
    },
    provides = [PluginModuleInfo],
)

def ij_plugin_module(
        name,
        module_name,
        non_classpath_data = {},
        packed_deps = [],
        visibility = None,
        **kwargs):
    """Defines a plugin module that can be included in ij_plugin.
    It can be either a content module or a plugin descriptor module.
    The macro delegates compilation to jvm_library and adds plugin packaging data to its output.

    Args:
        name: Target name
        module_name: Name of the content module or JPS module for the plugin descriptor module
        non_classpath_data: Targets that outputs should be included in the distribution mapped to relative output paths.
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
