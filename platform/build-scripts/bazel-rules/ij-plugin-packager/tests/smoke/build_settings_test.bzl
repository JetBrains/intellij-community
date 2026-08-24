_IDE_BUILD_NUMBER_SETTING = "//platform/build-scripts/bazel-rules/ij-ide-build-settings:ide_build_number"
_IDE_STABILITY_LEVEL_SETTING = "//platform/build-scripts/bazel-rules/ij-ide-build-settings:ide_stability_level"
_PLUGIN_FORCE_EXACT_BUILD_COMPATIBILITY_SETTING = "//platform/build-scripts/bazel-rules/ij-ide-build-settings:ij_plugin_force_exact_build_compatibility"
_PLUGIN_VERSION_SETTING = "//platform/build-scripts/bazel-rules/ij-ide-build-settings:ij_plugin_version"

def _build_settings_transition_impl(_settings, attr):
    return {
        _IDE_BUILD_NUMBER_SETTING: attr.ide_build_number,
        _IDE_STABILITY_LEVEL_SETTING: attr.ide_stability_level,
        _PLUGIN_FORCE_EXACT_BUILD_COMPATIBILITY_SETTING: attr.force_exact_build_compatibility,
        _PLUGIN_VERSION_SETTING: attr.plugin_version,
    }

_build_settings_transition = transition(
    implementation = _build_settings_transition_impl,
    inputs = [],
    outputs = [
        _IDE_BUILD_NUMBER_SETTING,
        _IDE_STABILITY_LEVEL_SETTING,
        _PLUGIN_FORCE_EXACT_BUILD_COMPATIBILITY_SETTING,
        _PLUGIN_VERSION_SETTING,
    ],
)

# A custom rule applies the transition, and its symlink gives each configured plugin
# a unique runfiles path without copying the plugin directory.
def _configured_plugin_impl(ctx):
    plugin = ctx.attr.plugin[0]
    input_directories = [file for file in plugin[DefaultInfo].files.to_list() if file.is_directory]
    if len(input_directories) != 1:
        fail("Expected exactly one plugin directory from %s, got %s" % (plugin.label, input_directories))

    output_directory = ctx.actions.declare_directory(ctx.label.name)
    ctx.actions.symlink(
        output = output_directory,
        target_file = input_directories[0],
    )
    return [DefaultInfo(
        files = depset([output_directory]),
        runfiles = ctx.runfiles([output_directory]),
    )]

_configured_plugin = rule(
    implementation = _configured_plugin_impl,
    attrs = {
        "force_exact_build_compatibility": attr.bool(),
        "ide_build_number": attr.string(),
        "ide_stability_level": attr.string(default = "snapshot"),
        "plugin": attr.label(
            cfg = _build_settings_transition,
            mandatory = True,
        ),
        "plugin_version": attr.string(),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

def ij_plugin_settings_test_target(
        name,
        plugin,
        ide_build_number = "",
        plugin_version = "",
        ide_stability_level = "snapshot",
        force_exact_build_compatibility = False):
    """Builds and exposes an ij_plugin target with the supplied IDE build settings."""
    _configured_plugin(
        name = name,
        force_exact_build_compatibility = force_exact_build_compatibility,
        ide_build_number = ide_build_number,
        ide_stability_level = ide_stability_level,
        plugin = plugin,
        plugin_version = plugin_version,
        testonly = True,
        visibility = ["//platform/build-scripts/bazel-rules/ij-plugin-packager/tests:__pkg__"],
    )
