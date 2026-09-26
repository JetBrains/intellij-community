IdeBuildNumberProvider = provider(fields = ["build_number"])
PluginVersionProvider = provider(fields = ["plugin_version"])
IdeStabilityLevelProvider = provider(fields = ["stability_level"])

stability_levels = [
    "snapshot",  # built from source code without fixed number
    "nightly",  # has a fixed number, but isn't supposed to be available for general audience
    "EAP",  # a public EAP or Beta build
    "release",  # a public release or release candidate build (`isEap=false` in ApplicationInfo.xml)
]

def _ide_build_number(ctx):
    build_number = ctx.build_setting_value
    if build_number:
        for component in build_number.split("."):
            if not component.isdigit():
                fail("build_number must contain only numbers separated by dots, got %r" % build_number)

    return [IdeBuildNumberProvider(build_number = build_number)]

ide_build_number = rule(
    implementation = _ide_build_number,
    build_setting = config.string(flag = True),
)

def _plugin_version(ctx):
    plugin_version = ctx.build_setting_value
    return [PluginVersionProvider(plugin_version = plugin_version)]

ij_plugin_version = rule(
    implementation = _plugin_version,
    build_setting = config.string(flag = True),
)

def _ide_stability_level(ctx):
    stability_level = ctx.build_setting_value
    if stability_level not in stability_levels:
        fail("ide_stability_level build setting can be one of [" + ", ".join(stability_levels) + "], got " + stability_level)
    return [IdeStabilityLevelProvider(stability_level = stability_level)]

ide_stability_level = rule(
    implementation = _ide_stability_level,
    build_setting = config.string(flag = True),
)
