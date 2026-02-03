load(
    "@rules_kotlin//kotlin/internal:defs.bzl",
    KotlinInfo = "KtJvmInfo",
    _KtCompilerPluginInfo = "KtCompilerPluginInfo",
    _KtPluginConfiguration = "KtPluginConfiguration",
)

visibility("private")

def exported_compiler_plugins_from(deps):
    """Encapsulates compiler dependency metadata."""
    plugins = []
    for dep in deps:
        if KotlinInfo in dep and dep[KotlinInfo] != None:
            plugins.extend(dep[KotlinInfo].exported_compiler_plugins.to_list())
    return plugins

def collect_compiler_plugins_for_export(local, exports):
    """Collects into a depset. """
    return depset(
        local,
        transitive = [
            e[KotlinInfo].exported_compiler_plugins
            for e in exports
            if KotlinInfo in e and e[KotlinInfo]
        ],
    )

def compiler_plugins_from(targets):
    """Returns a struct containing the plugin metadata for the given targets.

    Args:
        targets: A list of targets.
    Returns:
        A struct containing the plugins for the given targets in the format:
        {
            stubs_phase = {
                classpath = depset,
                options= List[KtCompilerPluginOption],
            ),
            compile = {
                classpath = depset,
                options = List[KtCompilerPluginOption],
            },
        }
    """

    all_plugins = {}
    plugins_without_phase = []
    for t in targets:
        if _KtCompilerPluginInfo not in t:
            continue
        plugin = t[_KtCompilerPluginInfo]
        if not (plugin.stubs or plugin.compile):
            plugins_without_phase.append("%s: %s" % (t.label, plugin.id))

        existing = all_plugins.get(plugin.id)
        if existing:
            if existing != plugin:
                fail("has multiple plugins with the same id: %s." % plugin.id)
        else:
            all_plugins[plugin.id] = plugin

    if plugins_without_phase:
        fail("has plugin without a phase defined: %s" % cfgs_without_plugin)

    plugin_id_to_configuration = {}
    cfgs_without_plugin = []
    for t in targets:
        if _KtPluginConfiguration not in t:
            continue
        cfg = t[_KtPluginConfiguration]
        if cfg.id not in all_plugins:
            cfgs_without_plugin.append("%s: %s" % (t.label, cfg.id))
        plugin_id_to_configuration[cfg.id] = cfg

    if cfgs_without_plugin:
        fail("has plugin configurations without corresponding plugins: %s" % cfgs_without_plugin)

    return struct(
        stubs_phase = [],
        compile_phase = _new_plugin_from(plugin_id_to_configuration, [p for p in all_plugins.values() if p.compile]),
    )

def _new_plugin_from(plugin_id_to_configuration, plugins_for_phase):
    classpath = {}
    options = {}
    for p in plugins_for_phase:
        if p.id in plugin_id_to_configuration:
            cfg = plugin_id_to_configuration[p.id]
            classpath[p.id] = depset(transitive = p.classpath + cfg.classpath)
            options[p.id] = p.options + cfg.options
        else:
            classpath[p.id] = p.classpath
            if p.options:
                options[p.id] = p.options

    return struct(
        classpath = classpath,
        options = options,
    )
