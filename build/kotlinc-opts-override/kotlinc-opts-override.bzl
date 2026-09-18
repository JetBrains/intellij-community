"""Command-line overrides for the boolean Kotlin compiler options.

`--@community//build/kotlinc-opts-override:<option>=true` turns the boolean `kt_kotlinc_options` attribute `<option>`
on for every Kotlin compilation, and `=false` turns it off. The override wins over the value the module's own
`kotlinc_opts` target declares. An unset flag keeps the declared value.

Only a flag, a `config_setting` and a `select()` are used, so the override survives a move to the official rules_kotlin.
"""

load("@bazel_skylib//rules:common_settings.bzl", "string_flag")

_PACKAGE = "//build/kotlinc-opts-override"

def kotlinc_opts_overrides(options):
    """Declares a tri-state flag and a `true` and a `false` `config_setting` for each boolean option in `options`."""
    for option in options:
        string_flag(
            name = option,
            build_setting_default = "",
            values = ["", "true", "false"],
        )
        for value in ["true", "false"]:
            native.config_setting(
                name = option + "=" + value,
                flag_values = {":" + option: value},
                visibility = ["//visibility:public"],
            )

def kotlinc_opt_override(option, default):
    """Returns `default` as a `select()` that the `<option>` flag can override."""

    # Label() resolves in the community repository, also when an ultimate module loads compiler-options.bzl.
    return select({
        Label(_PACKAGE + ":" + option + "=true"): True,
        Label(_PACKAGE + ":" + option + "=false"): False,
        "//conditions:default": default,
    })
