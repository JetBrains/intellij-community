load("@rules_java//java:defs.bzl", "JavaInfo", "java_import")
load("@rules_jvm//:jvm.bzl", "ResourceGroupInfo", "jvm_platform_transition")
load("@rules_kotlin//kotlin/internal:defs.bzl", KOTLIN_TOOLCHAIN = "TOOLCHAIN_TYPE")
load("//fleet/build/rules:haven_cli.bzl", "HAVEN_CLI_ATTR", "run_haven_cli")

def _fleet_plugin_services_resources_generate_impl(ctx):
    resources_output_dir = ctx.actions.declare_directory("%s.generated_resources" % ctx.label.name)
    resources_output_jar = ctx.actions.declare_file("%s.generated_resources.jar" % ctx.label.name)

    compile_classpath = depset(
        transitive = [
            dep[JavaInfo].transitive_compile_time_jars
            for dep in ctx.attr.deps
        ],
    )
    processor_classpath = ctx.attr._kernel_plugins_processor[JavaInfo].transitive_runtime_jars
    kotlin_toolchain = ctx.toolchains[KOTLIN_TOOLCHAIN]
    module_name = ctx.attr.module_name if ctx.attr.module_name else ctx.attr.name
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("generate-fleet-plugin-services-resources")
    args.add("--module-name=%s" % module_name)
    args.add_all(["--sources=%s" % s.path for s in ctx.files.srcs])
    args.add_all(["--classpath=%s" % c.path for c in compile_classpath.to_list()])
    args.add_all(["--processor-classpath=%s" % c.path for c in processor_classpath.to_list()])
    args.add("--jvm-target=%s" % kotlin_toolchain.jvm_target)
    args.add("--language-version=%s" % kotlin_toolchain.language_version)
    args.add("--api-version=%s" % kotlin_toolchain.api_version)
    args.add("--output-dir=%s" % resources_output_dir.path)
    args.add("--output-jar=%s" % resources_output_jar.path)

    run_haven_cli(
        ctx = ctx,
        mnemonic = "GenerateFleetPluginServicesResources",
        inputs = depset(
            direct = ctx.files.srcs,
            transitive = [compile_classpath, processor_classpath],
        ),
        outputs = [resources_output_dir, resources_output_jar],
        arguments = [args],
        progress_message = "Generating Fleet plugin services resources for %%{label}",
    )

    return [
        ResourceGroupInfo(files = [resources_output_dir], strip_prefix = resources_output_dir.path, add_prefix = ""),
        DefaultInfo(
            files = depset([resources_output_jar]),
        ),
    ]

_fleet_plugin_services_resources_generate = rule(
    implementation = _fleet_plugin_services_resources_generate_impl,
    attrs = HAVEN_CLI_ATTR | {
        "srcs": attr.label_list(
            allow_files = True,
            mandatory = True,
            doc = "Source files used to derive Fleet plugin service resources.",
        ),
        "deps": attr.label_list(
            providers = [JavaInfo],
            doc = "Compile classpath for the analyzed sources.",
        ),
        "module_name": attr.string(
            doc = "Kotlin module name for the analyzed sources.",
        ),
        "_kernel_plugins_processor": attr.label(
            default = "//fleet/build/kernel.plugins.processor",
            providers = [JavaInfo],
        ),
    },
    toolchains = [KOTLIN_TOOLCHAIN],
)

def _fleet_plugin_services_resources_expose_impl(ctx):
    return [
        ctx.attr.resource_jar[0][DefaultInfo],
        ctx.attr.resource_jar[0][JavaInfo],
        ctx.attr.resource_jar[0][OutputGroupInfo],
        ctx.attr.generated[ResourceGroupInfo],
    ]

_fleet_plugin_services_resources_expose = rule(
    implementation = _fleet_plugin_services_resources_expose_impl,
    attrs = {
        "generated": attr.label(
            mandatory = True,
            providers = [ResourceGroupInfo],
        ),
        "resource_jar": attr.label(
            doc = """The resource jar with the actual providers to support Bazel plugin.""",
            mandatory = True,
            cfg = jvm_platform_transition,
        ),
    },
)

def fleet_plugin_services_resources(name, srcs, deps, module_name = None):
    generate_resources_name = name + "_generate"
    generate_jar_lib_name = name + "_lib"

    # Generates both the resource jar (for kotlin backend rules implementation) and the generated resources (for jps implementation).
    _fleet_plugin_services_resources_generate(
        name = generate_resources_name,
        srcs = srcs,
        deps = deps,
        module_name = module_name,
    )

    # This rule is only needed to expose the JavaInfo provider of the generated resources jar
    java_import(
        name = generate_jar_lib_name,
        jars = [":" + generate_resources_name],
        visibility = ["//visibility:private"],
        tags = ["manual"],
    )

    # Dummy rule to expose the resource jar and the generated resources to their respective consumers.
    _fleet_plugin_services_resources_expose(
        name = name,
        generated = ":" + generate_resources_name,
        resource_jar = generate_jar_lib_name,
    )
