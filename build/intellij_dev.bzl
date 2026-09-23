"""Macros for IntelliJ-based IDE development builds."""

load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@rules_java//java:defs.bzl", "java_binary")
load(
    ":dev_launch_dependencies.bzl",
    "preloaded_downloads_data",
    "preloaded_downloads_flag",
    "preloaded_downloads_manifest_data",
    "preloaded_downloads_only_flag",
)

# Names the prepared distribution for whoever consumes one - `PreBuiltDevMain` when it is a launcher, the IDE Starter's
# prebuilt dev-build runner when it is a test. Keep in sync with `DevIdeConfig.CONFIG_PATH_PROPERTY`, which is where the
# reading side of this contract lives.
DEV_IDE_CONFIG_PATH_PROPERTY = "idea.ide.config.path"

def intellij_dev_dist_config(name, dist, visibility = None, tags = []):
    """A single-file label for an assembled dev distribution's config file, for `$(rlocationpath ...)`.

    That expansion takes a label naming exactly one file, which a dist target - two outputs, one of them declared rather
    than predeclared - is not. Its `ide_config` output group is how it gets one.

    A consumer declares both this and the dist itself in `data`, and they must stay siblings in the runfiles tree: the
    config names the home relatively, so that the pair survives being read from a different path than it was written to.

    `tags` is the filegroup's; a consumer that must stay out of a wildcard build passes `["manual"]`.
    """
    native.filegroup(
        name = name,
        srcs = [dist],
        output_group = "ide_config",
        tags = tags,
        visibility = visibility,
    )

DEFAULT_JVM_FLAGS = [
    "--enable-native-access=ALL-UNNAMED",
    "-ea",
    "-Didea.jre.check=true",
    "-Didea.is.internal=true",
    "-Didea.debug.mode=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
]

def _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path):
    """The flags an IDE needs to run, independent of how it was assembled.

    `$${...}` is a literal `${...}` the java stub expands at launch; `BUILD_WORKSPACE_DIRECTORY` is set by `bazel run`,
    so a launcher started any other way must export it itself.
    """

    # Use provided paths or defaults based on target name
    effective_config_path = config_path if config_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/config"
    effective_system_path = system_path if system_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/system"

    all_jvm_flags = DEFAULT_JVM_FLAGS + [
        "-Didea.plugins.path=" + effective_config_path + "/plugins",
        "-Didea.log.path=" + effective_system_path + "/log",
    ] + jvm_flags

    if platform_prefix:
        all_jvm_flags = all_jvm_flags + ["-Didea.platform.prefix=" + platform_prefix]

    all_jvm_flags = all_jvm_flags + [
        "-Didea.config.path=" + effective_config_path,
        "-Didea.system.path=" + effective_system_path,
    ]

    # On Windows the java stub folds a long classpath into one jar with a `Class-Path` manifest attribute.
    # `PathClassLoader` is the system class loader and expands that jar only with this flag.
    # https://github.com/bazelbuild/bazel/blob/93cde47ab3236b3b7124b41824f843f3659064de/src/tools/launcher/java_launcher.cc#L385
    return all_jvm_flags + select({
        "@bazel_tools//src/conditions:windows": ["-Didea.reset.classpath.from.manifest=true"],
        "//conditions:default": [],
    })

_DEV_MAIN_CLASS = "org.jetbrains.intellij.build.devServer.DevMainKt"
_PREBUILT_DEV_MAIN_CLASS = "org.jetbrains.intellij.build.devServer.PreBuiltDevMain"
_BEFORE_RUN_DEV_MAIN_CLASS = "org.jetbrains.intellij.build.devServer.BeforeRunDevMain"

def _before_run_launch(main_class, before_run_main_class, before_run_runtime_deps):
    """How a launcher starts: `main_class` directly, or `BeforeRunDevMain` over `before_run_main_class` and then `main_class`.

    `BeforeRunDevMain` starts `DevMainKt` unless `-Dintellij.build.dev.server.main.class` names another launcher, so
    only a launcher that is not `DevMainKt` passes the property.
    """
    runtime_deps = ["@community//platform/bootstrap/dev"]
    if not before_run_main_class:
        return struct(main_class = main_class, runtime_deps = runtime_deps, jvm_flags = [])
    jvm_flags = ["-Dintellij.build.dev.server.before.run.main.class=" + before_run_main_class]
    if main_class != _DEV_MAIN_CLASS:
        jvm_flags.append("-Dintellij.build.dev.server.main.class=" + main_class)
    return struct(
        main_class = _BEFORE_RUN_DEV_MAIN_CLASS,
        runtime_deps = runtime_deps + before_run_runtime_deps,
        jvm_flags = jvm_flags,
    )

def intellij_dev_binary(
        name,
        visibility,
        data,
        jvm_flags,
        env,
        platform_prefix,
        bazel_targets_json,
        config_path,
        system_path,
        additional_modules,
        program_args,
        preloaded_download_repos,
        preloaded_downloads_exhaustive_on,
        before_run_main_class = "",
        before_run_runtime_deps = []):
    all_jvm_flags = _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path) + [
        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % bazel_targets_json,
    ]

    if additional_modules:
        all_jvm_flags = all_jvm_flags + ["-Dadditional.modules=\"" + additional_modules + "\""]

    launch = _before_run_launch(_DEV_MAIN_CLASS, before_run_main_class, before_run_runtime_deps)
    main_class = launch.main_class
    runtime_deps = launch.runtime_deps
    all_jvm_flags = all_jvm_flags + launch.jvm_flags

    # The archives the assembly would otherwise download at launch, as runfiles for the host platform,
    # with their manifests. `preloaded_downloads_exhaustive_on` names the platforms where the declared set
    # was measured to be this product's whole set, so an undeclared URL is an error rather than a
    # download; a product that fetches its own archives - the CIDR toolchains, a locally overridden
    # front-end - has none. See PreloadedDownloads and the caller that decides.
    all_jvm_flags = all_jvm_flags + preloaded_downloads_flag(preloaded_download_repos)
    if preloaded_downloads_exhaustive_on:
        all_jvm_flags = all_jvm_flags + preloaded_downloads_only_flag(preloaded_downloads_exhaustive_on)
    preloaded_data = (
        preloaded_downloads_data(preloaded_download_repos) +
        preloaded_downloads_manifest_data(preloaded_download_repos)
    )

    java_binary(
        name = name,
        visibility = visibility,
        runtime_deps = runtime_deps,
        main_class = main_class,
        data = data + [bazel_targets_json] + preloaded_data,
        jvm_flags = all_jvm_flags,
        env = env,
        add_opens = INTELLIJ_ADD_OPENS,
        args = program_args,
    )

def intellij_dev_prebuilt_binary(
        name,
        dist,
        platform_prefix = None,
        jvm_flags = [],
        env = {},
        config_path = None,
        system_path = None,
        program_args = [],
        visibility = None,
        local_home_tool = None,
        data = [],
        before_run_main_class = "",
        before_run_runtime_deps = [],
        ide_config = None):
    """Launches a built distribution or a linked local home without packaging it.

    The distribution declares its product and additional modules.
    When it supplies local metadata, local_home_tool prepares a temporary home from its component runfiles.
    `data` is the launcher's extra runfiles, on top of the distribution and its config.
    With `before_run_main_class`, `BeforeRunDevMain` runs that class over `before_run_runtime_deps` first, then
    `PreBuiltDevMain`, as `intellij_dev_binary` does before `DevMainKt`.
    `ide_config` is the `intellij_dev_dist_config` of `dist` when several launchers share one distribution; `dist` then
    names a single target, and the macro declares no alias and no config of its own.
    """

    # Manual, like the distribution in `data`: a wildcard build must not compose it. `bazel run` names the launcher and
    # is not affected.
    tags = ["manual"]

    if ide_config:
        dist_target = dist
    else:
        ide_config = name + "_ide_config"
        dist_target = name + "_distribution"
        native.alias(name = dist_target, actual = dist, tags = tags, visibility = ["//visibility:private"])
        intellij_dev_dist_config(name = ide_config, dist = dist_target, tags = tags, visibility = ["//visibility:private"])

    local_home_data = [local_home_tool] if local_home_tool else []
    local_home_flags = ["-Didea.dev.local.home.tool=$(rlocationpath %s)" % local_home_tool] if local_home_tool else []
    launch = _before_run_launch(_PREBUILT_DEV_MAIN_CLASS, before_run_main_class, before_run_runtime_deps)

    java_binary(
        name = name,
        visibility = visibility,
        runtime_deps = launch.runtime_deps,
        main_class = launch.main_class,
        tags = tags,
        data = data + [dist_target, ide_config] + local_home_data,
        jvm_flags = _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path) + local_home_flags + launch.jvm_flags + [
            "-D%s=$(rlocationpath %s)" % (DEV_IDE_CONFIG_PATH_PROPERTY, ide_config),
            # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime,
            # and a dev launch has it only because `DevMainImpl` sets it from the project root it just built against.
            "-Didea.dev.project.root=$${BUILD_WORKSPACE_DIRECTORY}",
        ],
        env = env,
        add_opens = INTELLIJ_ADD_OPENS,
        args = program_args,
    )
