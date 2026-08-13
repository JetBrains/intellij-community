"""Builds independently cacheable platform and plugin parts of a dev-mode IDE distribution.

Split assembly deliberately supports only builds with scrambling disabled. Platform co-scrambling and
per-plugin scrambling require both component layouts in one process and remain a known limitation.
"""

load("@community//build:project_model_manifest.bzl", "write_project_model_manifest")
load("//build:dev_launch_dependencies.bzl", "platform_parts")

IntellijDevPlatformInfo = provider(
    fields = {
        "home": "The platform component tree.",
        "manifest": "The platform component manifest.",
    },
)

IntellijDevPluginsInfo = provider(
    fields = {
        "home": "The plugins overlay tree.",
        "manifest": "The plugins component manifest.",
    },
)

IntellijDevDistInfo = provider(
    fields = {
        "home": "The composed IDE home directory.",
        "ide_config": "The config file used by PreBuiltDevMain.",
    },
)

def _write_bazel_inputs_manifest(ctx):
    if len(ctx.attr.module_outputs) != len(ctx.attr.module_output_labels):
        fail("module_outputs and module_output_labels must have the same length")
    lines = []
    files = []
    for target, label in zip(ctx.attr.module_outputs, ctx.attr.module_output_labels):
        target_files = target[DefaultInfo].files.to_list()
        if len(target_files) != 1:
            fail("%s must provide exactly one file, got %s" % (target.label, target_files))
        file = target_files[0]
        files.append(file)
        lines.append("%s\t%s" % (label, file.path))
    manifest = ctx.actions.declare_file(ctx.label.name + ".bazel-inputs")
    ctx.actions.write(manifest, "\n".join(lines) + "\n")
    return manifest, files

def _add_target_platform_args(args, target_platform):
    if target_platform:
        target_parts = platform_parts(target_platform)
        args.add("--os=" + ("macos" if target_parts.os == "darwin" else target_parts.os))
        args.add("--arch=" + target_parts.arch)

def _component_action(ctx, kind, additional_modules, test_output_modules, provider):
    home = ctx.actions.declare_directory(ctx.label.name + "." + kind)
    component_manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    scratch = ctx.actions.declare_directory(ctx.label.name + ".scratch")
    unused_inputs = ctx.actions.declare_file(ctx.label.name + ".unused-inputs")

    project_files = ctx.files.project_model_files + ctx.files.extra_project_files
    project_manifest = write_project_model_manifest(ctx, ctx.label.name + ".project.manifest", project_files, ctx.attr.mode)
    bazel_inputs_manifest, module_outputs = _write_bazel_inputs_manifest(ctx)

    args = ctx.actions.args()
    args.add("--project-manifest=" + project_manifest.path)
    args.add("--output-dir=" + home.path)
    args.add("--component-manifest=" + component_manifest.path)
    args.add("--scratch-dir=" + scratch.path)
    args.add("--clean-scratch-on-success")
    args.add("--build-part=" + kind)
    args.add("--platform-prefix=" + ctx.attr.platform_prefix)
    args.add("--bazel-targets-json=" + ctx.file.bazel_targets_json.path)
    args.add("--bazel-inputs-manifest=" + bazel_inputs_manifest.path)
    args.add("--unused-inputs=" + unused_inputs.path)
    _add_target_platform_args(args, ctx.attr.target_platform)
    args.add_all(additional_modules, format_each = "--additional-module=%s")
    args.add_all(test_output_modules, format_each = "--test-output-module=%s")
    args.add_all(ctx.files.preloaded_manifests, format_each = "--preloaded-manifest=%s")
    if ctx.attr.preloaded_only:
        args.add("--preloaded-only")

    ctx.actions.run(
        inputs = depset(
            direct = project_files + [
                project_manifest,
                bazel_inputs_manifest,
                ctx.file.bazel_targets_json,
            ] + module_outputs + ctx.files.preloaded_downloads + ctx.files.preloaded_manifests,
        ),
        outputs = [home, component_manifest, scratch, unused_inputs],
        executable = ctx.executable.assembler,
        arguments = [args],
        execution_requirements = {"local": "1", "no-remote-cache": "1"},
        unused_inputs_list = unused_inputs,
        mnemonic = "IntellijDev%s" % kind.title(),
        progress_message = "Assembling %s %s component %s" % (ctx.attr.platform_prefix, kind, ctx.label),
    )
    return [
        DefaultInfo(files = depset([home, component_manifest])),
        provider(home = home, manifest = component_manifest),
    ]

def _platform_impl(ctx):
    return _component_action(ctx, "platform", [], [], IntellijDevPlatformInfo)

def _plugins_impl(ctx):
    return _component_action(ctx, "plugins", ctx.attr.additional_modules, ctx.attr.test_output_modules, IntellijDevPluginsInfo)

_component_attrs = {
    "assembler": attr.label(executable = True, cfg = "target", mandatory = True),
    "mode": attr.string(default = "ultimate", values = ["community", "ultimate"]),
    "platform_prefix": attr.string(mandatory = True),
    "target_platform": attr.string(default = ""),
    "project_model_files": attr.label_list(allow_files = True, mandatory = True),
    "extra_project_files": attr.label_list(allow_files = True),
    "bazel_targets_json": attr.label(allow_single_file = True, mandatory = True),
    "module_outputs": attr.label_list(allow_files = True, mandatory = True),
    "module_output_labels": attr.string_list(mandatory = True),
    "preloaded_downloads": attr.label_list(allow_files = True),
    "preloaded_manifests": attr.label_list(allow_files = True),
    "preloaded_only": attr.bool(default = False),
}

intellij_dev_platform = rule(
    implementation = _platform_impl,
    attrs = _component_attrs,
)

intellij_dev_plugins = rule(
    implementation = _plugins_impl,
    attrs = dict(
        _component_attrs,
        additional_modules = attr.string_list(),
        test_output_modules = attr.string_list(),
    ),
)

def _compose_impl(ctx):
    home = ctx.actions.declare_directory(ctx.label.name + ".dist")
    ide_config = ctx.actions.declare_file(ctx.label.name + ".ide.config")
    platform = ctx.attr.platform[IntellijDevPlatformInfo]
    plugins = ctx.attr.plugins[IntellijDevPluginsInfo]
    args = ctx.actions.args()
    args.add("--platform-dir=" + platform.home.path)
    args.add("--platform-manifest=" + platform.manifest.path)
    args.add("--plugins-dir=" + plugins.home.path)
    args.add("--plugins-manifest=" + plugins.manifest.path)
    args.add("--output-dir=" + home.path)
    args.add("--ide-config=" + ide_config.path)
    ctx.actions.run(
        inputs = [platform.home, platform.manifest, plugins.home, plugins.manifest],
        outputs = [home, ide_config],
        executable = ctx.executable.composer,
        arguments = [args],
        execution_requirements = {"local": "1", "no-remote-cache": "1"},
        mnemonic = "IntellijDevDistCompose",
        progress_message = "Composing dev distribution %s" % ctx.label,
    )
    return [
        DefaultInfo(files = depset([home, ide_config])),
        IntellijDevDistInfo(home = home, ide_config = ide_config),
        OutputGroupInfo(ide_config = depset([ide_config]), home = depset([home])),
    ]

intellij_dev_dist = rule(
    implementation = _compose_impl,
    attrs = {
        "composer": attr.label(executable = True, cfg = "target", mandatory = True),
        "platform": attr.label(providers = [IntellijDevPlatformInfo], mandatory = True),
        "plugins": attr.label(providers = [IntellijDevPluginsInfo], mandatory = True),
    },
)
