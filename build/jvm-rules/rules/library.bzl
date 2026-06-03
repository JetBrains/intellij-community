load("@rules_java//java:defs.bzl", _JavaInfo = "JavaInfo")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo")
load("//:rules/common-attrs.bzl", "USE_RULES_KOTLIN_BACKEND", "add_dicts", "common_attr", "common_outputs", "common_toolchains")
load("//:rules/impl/compile.bzl", "kt_jvm_produce_jar_actions")
load("//:rules/impl/transitions.bzl", "jvm_platform_transition")
load("//:rules/resource.bzl", "ResourceGroupInfo")

visibility("public")

def _jvm_library(ctx):
    if ctx.attr.neverlink and ctx.attr.runtime_deps:
        fail("runtime_deps and neverlink is nonsensical.", attr = "runtime_deps")

    providers = kt_jvm_produce_jar_actions(ctx, False)
    files = [ctx.outputs.jar]
    kotlin_cri_storage_file = providers.kt.outputs.kotlin_cri_storage_file
    if kotlin_cri_storage_file:
        files.append(kotlin_cri_storage_file)
    return [
        providers.java,
        providers.kt,
        DefaultInfo(
            files = depset(files),
            runfiles = ctx.runfiles(
                # explicitly include data files, otherwise they appear to be missing
                files = ctx.files.data,
                transitive_files = depset(order = "default"),
                # continue to use collect_default until proper transitive data collecting is implemented.
                collect_default = True,
            ),
        ),
    ]

_jvm_library_jps = rule(
    doc = """JPS-based implementation: compiles and links Kotlin and Java sources into a .jar file.""",
    attrs = add_dicts(common_attr, {
        "srcs": attr.label_list(
            doc = """The list of source files that are processed to create the target, this can contain both Java and Kotlin
                     files. Java analysis occurs first so Kotlin classes may depend on Java classes in the same compilation unit.""",
            default = [],
            allow_files = [".kt", ".java", ".srcjar", ".form"],
            mandatory = True,
        ),
        "resources": attr.label_list(
            doc = """The list of resource files.""",
            default = [],
            allow_files = True,
        ),
        "strip_prefix": attr.label(
            doc = """The path prefix to remove from Java resources""",
            allow_single_file = True,
        ),
        "resource_strip_prefix": attr.string(
            doc = """\
                Equivalent of `strip_prefix`, but includes the package path as a prefix.
                Its name and value follow the standard Bazel convention and are properly recognized by the Bazel plugin.
                It's only added to satisfy the plugin. Monorepo rules use `strip_prefix` value instead.
                """,
            default = "",
        ),
        "resource_jars": attr.label_list(
            doc = """The remaining resource groups passed as resource jars.""",
            default = [],
            providers = [ResourceGroupInfo],
        ),
        "exported_compiler_plugins": attr.label_list(
            doc = """\
    Exported compiler plugins.

    Compiler plugins listed here will be treated as if they were added in the plugins attribute
    of any targets that directly depend on this target. Unlike `java_plugin`s exported_plugins,
    this is not transitive""",
            default = [],
            providers = [[_KtCompilerPluginInfo]],
        ),
        "exports": attr.label_list(
            doc = """\
            Exported libraries.

            Deps listed here will be made available to other rules, as if the parents explicitly depended on
            these deps. This is not true for regular (non-exported) deps.""",
            default = [],
            providers = [_JavaInfo],
        ),
        "neverlink": attr.bool(
            doc = """If true only use this library for compilation and not at runtime.""",
            default = False,
        ),
        "_empty_jar": attr.label(
            doc = """Empty jar for exporting JavaInfos.""",
            allow_single_file = True,
            cfg = "target",
            default = Label("@rules_kotlin//third_party:empty.jar"),
        ),
    }),
    outputs = common_outputs,
    toolchains = common_toolchains,
    fragments = ["java"],  # required fragments of the target configuration
    host_fragments = ["java"],  # required fragments of the host configuration
    implementation = _jvm_library,
    provides = [_JavaInfo, _KtJvmInfo],
    cfg = jvm_platform_transition,
)

def jvm_library(
        name,
        srcs = [],
        deps = [],
        exports = [],
        runtime_deps = [],
        resources = [],
        resource_strip_prefix = None,
        resource_jars = [],
        neverlink = False,
        plugins = [],
        module_name = None,
        kotlinc_opts = None,
        javac_opts = None,
        exported_compiler_plugins = [],
        associates = [],
        data = [],
        visibility = None,
        tags = [],
        use_rules_kotlin_backend = USE_RULES_KOTLIN_BACKEND,
        **kwargs):
    """Macro that creates jvm_library using the configured backend.

    Args:
        name: Target name
        srcs: Source files (.kt, .java)
        deps: Dependencies
        exports: Exported dependencies
        runtime_deps: Runtime-only dependencies
        resources: Resource files.
        resource_strip_prefix: Label for the path prefix to strip from resources.
        resource_jars: Remaining resource groups (ResourceGroupInfo targets).
        neverlink: If true, only use for compilation
        plugins: Compiler plugins
        module_name: Kotlin module name
        kotlinc_opts: Kotlin compiler options label
        javac_opts: Java compiler options label
        exported_compiler_plugins: Exported compiler plugins
        associates: Kotlin associate modules
        data: Data files
        visibility: Target visibility
        tags: Target tags
        **kwargs: Additional arguments passed to the selected backend
    """

    effective_kotlinc_opts = kotlinc_opts if kotlinc_opts != None else Label("//:default-kotlinc-opts")

    if use_rules_kotlin_backend:
        kt_jvm_library(
            name = name,
            srcs = srcs,
            deps = deps,
            exports = exports,
            runtime_deps = runtime_deps,
            resources = resources,
            resource_strip_prefix = resource_strip_prefix,
            resource_jars = resource_jars,
            neverlink = neverlink,
            plugins = plugins,
            module_name = module_name,
            kotlinc_opts = effective_kotlinc_opts,
            javac_opts = javac_opts,
            exported_compiler_plugins = exported_compiler_plugins,
            associates = associates,
            data = data,
            visibility = visibility,
            tags = tags,
            **kwargs
        )
    else:
        package_name = native.package_name()
        if package_name:
            package_name = package_name + "/"
        standard_resource_strip_prefix = ""
        if resource_strip_prefix:
            standard_resource_strip_prefix = package_name + resource_strip_prefix
        _jvm_library_jps(
            name = name,
            srcs = srcs,
            deps = deps,
            exports = exports,
            runtime_deps = runtime_deps,
            resources = resources,
            strip_prefix = resource_strip_prefix,
            resource_strip_prefix = standard_resource_strip_prefix,
            resource_jars = resource_jars,
            neverlink = neverlink,
            plugins = plugins,
            module_name = module_name,
            kotlinc_opts = effective_kotlinc_opts,
            javac_opts = javac_opts,
            exported_compiler_plugins = exported_compiler_plugins,
            associates = associates,
            data = data,
            visibility = visibility,
            tags = tags,
            **kwargs
        )
