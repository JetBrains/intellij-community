load("@rules_java//java:defs.bzl", _JavaInfo = "JavaInfo")
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_kotlin//kotlin/internal:defs.bzl", "KtPluginConfiguration", _KtCompilerPluginInfo = "KtCompilerPluginInfo", _KtJvmInfo = "KtJvmInfo")
load("//:rules/common-attrs.bzl", "add_dicts", "common_attr", "common_outputs", "common_toolchains")
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
            allow_files = [".kt", ".java", ".form"],
            mandatory = True,
        ),
        "resources": attr.label_list(
            doc = """The list of resource groups to create the target.""",
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

def _jvm_transition_copy_jar_impl(ctx):
    output = ctx.actions.declare_file(ctx.attr.out)
    ctx.actions.symlink(
        output = output,
        target_file = ctx.file.src,
    )
    return [DefaultInfo(files = depset([output]))]

_jvm_transition_copy_jar = rule(
    implementation = _jvm_transition_copy_jar_impl,
    attrs = {
        "src": attr.label(mandatory = True, allow_single_file = True),
        "out": attr.string(mandatory = True),
    },
    cfg = jvm_platform_transition,
)

# Config setting labels for cross-repo select() compatibility.
#_RULES_KOTLIN_ENABLED = Label("//:rules_kotlin_enabled")

def jvm_library(
        name,
        srcs = [],
        deps = [],
        exports = [],
        runtime_deps = [],
        resources = [],
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
        **kwargs):
    """Macro that creates jvm_library with configurable backend.

    By default uses JPS-based implementation (rules_jvm).
    With --@rules_jvm//:use_rules_kotlin=True, uses rules_kotlin backend.

    Args:
        name: Target name
        srcs: Source files (.kt, .java)
        deps: Dependencies
        exports: Exported dependencies
        runtime_deps: Runtime-only dependencies
        resources: Resource groups (ResourceGroupInfo).
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
        **kwargs: Additional arguments passed to both backends
    """

    jps_name = name
    #kt_name = "_%s__kt" % name
    effective_kotlinc_opts = kotlinc_opts if kotlinc_opts != None else Label("//:default-kotlinc-opts")

    # JPS implementation (always created)
    _jvm_library_jps(
        name = jps_name,
        srcs = srcs,
        deps = deps,
        exports = exports,
        runtime_deps = runtime_deps,
        resources = resources,
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

    # rules_kotlin implementation
    # resourcegroup targets now also emit resource jars (DefaultInfo), so they can be forwarded via resource_jars.
    #kt_jvm_library(
    #    name = kt_name,
    #    srcs = srcs,
    #    deps = deps,
    #    exports = exports,
    #    runtime_deps = runtime_deps,
    #    resource_jars = resources,
    #    neverlink = neverlink,
    #    plugins = plugins,
    #    module_name = module_name,
    #    kotlinc_opts = effective_kotlinc_opts,
    #    javac_opts = javac_opts,
    #    exported_compiler_plugins = exported_compiler_plugins,
    #    associates = associates,
    #    data = data,
    #    visibility = ["//visibility:private"],
    #    tags = tags + ["manual"],
    #    **kwargs
    #)

    # In Bazel, it's impossible to conditionally create a target, so we always create both rules_jvm and rules_kotlin versions
    # and expose either one based on the flag
    #native.alias(
    #    name = name,
    #    actual = select({
    #        _RULES_KOTLIN_ENABLED: ":" + kt_name,
    #        "//conditions:default": ":" + jps_name,
    #    }),
    #    visibility = visibility,
    #)

    # Expose jar with canonical name so that "name.jar" works as a target (it was an implicit output in rules_jvm-only world)
    #_jvm_transition_copy_jar(
    #    name = name + ".jar",
    #    src = select({
    #        _RULES_KOTLIN_ENABLED: ":" + kt_name + ".jar",
    #        "//conditions:default": ":" + jps_name + ".jar",
    #    }),
    #    out = name + ".jar",
    #    visibility = visibility,
    #    tags = tags + ["manual"],
    #)
