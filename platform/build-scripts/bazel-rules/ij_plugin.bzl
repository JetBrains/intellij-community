load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load(
    "@community//platform/build-scripts/bazel-rules/ij-ide-build-settings:defs.bzl",
    _IdeBuildNumberProvider = "IdeBuildNumberProvider",
    _IdeStabilityLevelProvider = "IdeStabilityLevelProvider",
    _PluginVersionProvider = "PluginVersionProvider",
)
load("@rules_java//java/common:java_common.bzl", "java_common")
load("@rules_jvm//:jvm.bzl", _jvm_platform_transition = "jvm_platform_transition", _scrubbed_host_platform_transition = "scrubbed_host_platform_transition")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

def _ij_plugin_impl(ctx):
    plugin_descriptor_module_info = ctx.attr.descriptor_module[_KtJvmInfo]
    dir_name = _module_name_to_directory_name(plugin_descriptor_module_info.module_name)
    output_dir = ctx.actions.declare_directory(dir_name)
    packed_modules_file = ctx.actions.declare_file("packed-modules.yaml")

    ide_build_number = ctx.attr._ide_build_number[_IdeBuildNumberProvider].build_number
    explicit_plugin_version = ctx.attr._plugin_version[_PluginVersionProvider].plugin_version
    default_ide_build_number_file = ctx.file._default_ide_build_number_file
    ide_stability_level = ctx.attr._ide_stability_level[_IdeStabilityLevelProvider].stability_level
    force_exact_build_compatibility = ctx.attr._force_exact_build_compatibility[BuildSettingInfo].value

    plugin_version = _computePluginVersion(explicit_plugin_version, ide_build_number)
    use_exact_build_compatibility = force_exact_build_compatibility or ctx.attr.until_build == "$since_build"
    since_build = _computeSinceBuild(ctx.attr.since_build, ide_build_number, ide_stability_level, use_exact_build_compatibility)
    until_build = _computeUntilBuild(ctx.attr.until_build, since_build, ide_build_number, ide_stability_level, use_exact_build_compatibility)

    plugin_descriptor_jar = plugin_descriptor_module_info.all_output_jars[0]
    inputs = [plugin_descriptor_jar]

    # Files are added to `args` as artifacts and not as strings, so their paths are rewritten when path mapping is enabled.
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add_all([output_dir], expand_directories = False)

    args.add("--plugin_version")
    args.add(plugin_version)
    args.add("--since_build")
    args.add(since_build)
    args.add("--until_build")
    args.add(until_build)
    if plugin_version == _build_number_from_file or since_build == _build_number_from_file or until_build == _build_number_from_file:
        args.add("--build_number_file")
        args.add(default_ide_build_number_file)
        inputs.append(default_ide_build_number_file)

    args.add("--packed_modules")
    args.add(packed_modules_file)
    args.add_all(
        "--descriptor_module",
        [plugin_descriptor_jar],
        format_each = plugin_descriptor_module_info.module_name + ":%s",
    )
    for content_module in ctx.attr.content_modules:
        content_module_info = content_module[_KtJvmInfo]
        content_module_jar = content_module_info.all_output_jars[0]
        args.add_all(
            "--content_module",
            [content_module_jar],
            format_each = content_module_info.module_name + ":%s",
        )
        inputs.append(content_module_jar)

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]
    ctx.actions.run(
        # the JBR files are intentionally not declared as inputs: their exec paths and digests are platform-specific,
        # so they would make the action key differ across Linux/macOS/Windows (see `bazel_scrubbing.cfg`).
        # `JvmCompile` in `@rules_jvm` relies on the same JBR-is-available-in-the-exec-root assumption.
        inputs = depset(inputs),
        outputs = [output_dir, packed_modules_file],
        tools = [ctx.file._packager_launcher, ctx.file._packager],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._packager_jvm_flags[BuildSettingInfo].value + [
            ctx.file._packager_launcher.path,
            ctx.file._packager.path,
            args,
        ],
        mnemonic = "IjPluginPackaging",
        progress_message = "Packaging plugin %{label}",
    )
    return [
        DefaultInfo(files = depset([output_dir, packed_modules_file])),
    ]

_build_number_from_file = "$build_number_from_file"

def _computePluginVersion(explicit_plugin_version, ide_build_number):
    if explicit_plugin_version:
        return explicit_plugin_version
    if ide_build_number:
        return ide_build_number
    return _build_number_from_file

def _computeSinceBuild(since_build, ide_build_number, ide_stability_level, use_exact_build_compatibility):
    if since_build != "$auto":
        return since_build
    if not ide_build_number:
        return _build_number_from_file
    components = ide_build_number.split(".")
    if use_exact_build_compatibility or ide_stability_level != "release" or len(components) <= 2:
        return ide_build_number
    return ".".join(components[:-1])

def _computeUntilBuild(until_build, since_build, ide_build_number, ide_stability_level, use_exact_build_compatibility):
    if use_exact_build_compatibility:
        return since_build
    if until_build != "$auto":
        return until_build
    if not ide_build_number:
        return _build_number_from_file
    components = ide_build_number.split(".")
    if ide_stability_level == "release":
        return components[0] + ".*"
    return ".".join(components[:-1]) + ".*"

def _module_name_to_directory_name(module_name):
    if module_name.startswith("intellij."):
        module_name = module_name[len("intellij."):]
    return module_name.replace(".", "-")

ij_plugin = rule(
    doc = """\
Builds a directory containing a plugin distribution for IntelliJ-based IDEs.

The rule also writes `packed-modules.yaml` beside that directory. The file names each jar in the distribution, and under
each jar the modules the packager put into it.

This rule is experimental, and its API may change. Do not migrate plugins to it yet.
""",
    attrs = {
        "_packager": attr.label(
            default = Label("//platform/build-scripts/bazel-rules/ij-plugin-packager:ij-plugin-packager_deploy.jar"),
            allow_single_file = True,
            # the deploy jar is platform-independent, so build it under a host-independent output directory to keep the
            # `IjPluginPackaging` action key (and thus its remote cache entries) the same on Linux/macOS/Windows
            cfg = _scrubbed_host_platform_transition,
        ),
        "_packager_jvm_flags": attr.label(
            default = Label("//platform/build-scripts/bazel-rules/ij-plugin-packager:ij-plugin-packager-jvm_flags"),
        ),
        "_packager_launcher": attr.label(
            default = Label("@rules_jvm//:rules/impl/MemoryLauncher.java"),
            allow_single_file = True,
        ),
        "_tool_java_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
        ),
        "_default_ide_build_number_file": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
        ),
        "_ide_build_number": attr.label(
            default = Label("@community//platform/build-scripts/bazel-rules/ij-ide-build-settings:ide_build_number"),
        ),
        "_plugin_version": attr.label(
            default = Label("@community//platform/build-scripts/bazel-rules/ij-ide-build-settings:ij_plugin_version"),
        ),
        "_ide_stability_level": attr.label(
            default = Label("@community//platform/build-scripts/bazel-rules/ij-ide-build-settings:ide_stability_level"),
        ),
        "_force_exact_build_compatibility": attr.label(
            default = Label("@community//platform/build-scripts/bazel-rules/ij-ide-build-settings:ij_plugin_force_exact_build_compatibility"),
        ),
        "descriptor_module": attr.label(
            doc = "A target containing the plugin descriptor (`META-INF/plugin.xml`).",
            providers = [_KtJvmInfo],
            mandatory = True,
        ),
        "content_modules": attr.label_list(
            doc = "A list of targets that produce the plugin content modules registered in the plugin.",
            providers = [_KtJvmInfo],
        ),
        "since_build": attr.string(
            doc = """\
The minimum IDE build number compatible with the plugin.

The value is used as the `since-build` attribute in the `idea-version` tag in `plugin.xml`.

The default value, `$auto`, is computed from the target IDE's build number and stability level, as specified by the `ide_build_number` and `ide_stability_level` settings,
and also depends on the value of `ij_plugin_force_exact_build_compatibility` setting and `until_build` attribute:

- For `release` builds the value is the build number without its third component, if the compatibility range isn't limited to the single IDE build
  via `ij_plugin_force_exact_build_compatibility` setting or `until_build` attribute. This makes the plugin compatible with other builds from the release branch.
- In other cases, the value is the exact build number.
""",
            default = "$auto",
        ),
        "until_build": attr.string(
            doc = """\
The maximum IDE build number compatible with the plugin.

The value is used as the `until-build` attribute in the `idea-version` tag in `plugin.xml`.

The following placeholders are supported:

- `$since_build`: Uses the `since-build` value, making the plugin compatible with only one IDE build.
- `$auto`: Computes the value from the `ide_stability_level` and `ij_plugin_force_exact_build_compatibility` settings:
    - If `ij_plugin_force_exact_build_compatibility` is enabled, uses the `since-build` value.
    - For `release` builds, adds `.*` to the first component in `since-build`, making the plugin compatible with all newer IDE builds of the same major version.
    - For other builds, replaces the last component in `since-build` with `*`, making the plugin compatible with all newer IDE builds from the same branch.
""",
            default = "$auto",
        ),
    },
    implementation = _ij_plugin_impl,
    cfg = _jvm_platform_transition,
)
