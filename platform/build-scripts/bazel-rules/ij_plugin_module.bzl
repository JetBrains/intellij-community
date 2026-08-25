load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_jvm//:jvm.bzl", _jvm_library = "jvm_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

PluginModuleInfo = provider(
    fields = {
        "module_name": "The module name (from the `<module>` tag for a content module, or the JPS module name for a plugin descriptor module).",
        "all_output_jars": "All JARs that should be packed together with the module inside the plugin distribution",
    },
)

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
        ),
    ]

_ij_plugin_module = rule(
    implementation = _ij_plugin_module_impl,
    attrs = {
        "module": attr.label(
            mandatory = True,
            providers = [[JavaInfo, _KtJvmInfo]],
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
        packed_deps = [],
        visibility = None,
        **kwargs):
    """Defines a plugin module that can be included in ij_plugin.
    It can be either a content module or a plugin descriptor module.
    The macro delegates compilation to jvm_library and adds plugin packaging data to its output.

    Args:
        name: Target name
        module_name: Name of the content module or JPS module for the plugin descriptor module
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
        packed_deps = packed_deps,
        tags = kwargs.get("tags", []),
        testonly = kwargs.get("testonly", False),
        visibility = visibility,
    )
