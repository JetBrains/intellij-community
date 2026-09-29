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
        fail("has plugin without a phase defined: %s" % plugins_without_phase)

    plugin_id_to_configurations = {}
    cfgs_without_plugin = []
    for t in targets:
        if _KtPluginConfiguration not in t:
            continue
        cfg = t[_KtPluginConfiguration]
        if cfg.id not in all_plugins:
            cfgs_without_plugin.append("%s: %s" % (t.label, cfg.id))
        plugin_id_to_configurations.setdefault(cfg.id, []).append(cfg)

    if cfgs_without_plugin:
        fail("has plugin configurations without corresponding plugins: %s" % cfgs_without_plugin)

    return struct(
        stubs_phase = [],
        compile_phase = _new_plugin_from(plugin_id_to_configurations, [p for p in all_plugins.values() if p.compile]),
    )

def _new_plugin_from(plugin_id_to_configurations, plugins_for_phase):
    """Merges every kt_plugin_cfg of a plugin into the plugin, as rules_kotlin does: the options follow the plugin's own
    options in target order, and the classpaths join."""
    classpath = {}
    options = {}
    data = {}
    for p in plugins_for_phase:
        cfgs = plugin_id_to_configurations.get(p.id, [])
        classpath[p.id] = depset(transitive = [p.classpath] + [cfg.classpath for cfg in cfgs])
        plugin_options = list(p.options)
        for cfg in cfgs:
            plugin_options.extend(cfg.options)
        if plugin_options:
            options[p.id] = plugin_options

        # kt_plugin_cfg carries the runfiles of its deps as data; the worker gets their files as inputs, like the plugin
        # classpath. A depset is accepted too. The files that $(location) names in an option value are not in the
        # provider, so they are not inputs.
        data_files = []
        for cfg in cfgs:
            if type(cfg.data) == "depset":
                data_files.append(cfg.data)
            elif type(cfg.data) == "runfiles":
                data_files.append(cfg.data.files)
        if data_files:
            data[p.id] = depset(transitive = data_files)

    return struct(
        classpath = classpath,
        options = options,
        data = data,
    )
