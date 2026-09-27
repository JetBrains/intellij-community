"""Macros for IntelliJ-based IDE development builds."""

load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@rules_java//java:defs.bzl", "java_binary")

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

# The data directories of the IDE. `_runtime_jvm_flags` owns them, and a flag that states one would lose to its defaults.
_LAUNCHER_DATA_PROPERTIES = ["idea.config.path", "idea.system.path", "idea.log.path"]

def _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path):
    """The flags an IDE needs to run, independent of how it was assembled.

    `$${...}` is a literal `${...}` the java stub expands at launch; `BUILD_WORKSPACE_DIRECTORY` is set by `bazel run`,
    so a launcher started any other way must export it itself.

    The config and system directories are `out/dev-data/<name>/config` and `out/dev-data/<name>/system` unless
    `config_path` and `system_path` say otherwise. On macOS the Go launcher makes `out/dev-data` a link to a directory
    outside the workspace. `jvm_flags` must not state a data directory.
    """
    for flag in jvm_flags:
        for key in _LAUNCHER_DATA_PROPERTIES:
            if flag.startswith("-D%s=" % key):
                fail("%s: `%s` states a data directory, which the launcher owns; use `config_path` or `system_path`" % (name, flag))

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

_PREBUILT_DEV_MAIN_CLASS = "com.intellij.platform.bootstrap.dev.PreBuiltDevMain"

# `PreBuiltDevMain`. The module carries no build scripts.
_LAUNCHER_MODULE = "@community//platform/bootstrap/dev"

# For `intellij_dev_legacy.bzl`, which cannot load a private name.
runtime_jvm_flags = _runtime_jvm_flags

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
        data = []):
    """Launches a built distribution or a linked local home without packaging it.

    The distribution declares its product and additional modules.
    When it supplies local metadata, local_home_tool prepares a temporary home from its component runfiles.
    `data` is the launcher's extra runfiles, on top of the distribution and its config.
    """

    # Manual, like the distribution in `data`: a wildcard build must not compose it. `bazel run` names the launcher and
    # is not affected.
    tags = ["manual"]

    ide_config = name + "_ide_config"
    dist_target = name + "_distribution"
    native.alias(name = dist_target, actual = dist, tags = tags, visibility = ["//visibility:private"])
    intellij_dev_dist_config(name = ide_config, dist = dist_target, tags = tags, visibility = ["//visibility:private"])

    local_home_data = [local_home_tool] if local_home_tool else []
    local_home_flags = ["-Didea.dev.local.home.tool=$(rlocationpath %s)" % local_home_tool] if local_home_tool else []

    java_binary(
        name = name,
        visibility = visibility,
        runtime_deps = [_LAUNCHER_MODULE],
        main_class = _PREBUILT_DEV_MAIN_CLASS,
        tags = tags,
        data = data + [dist_target, ide_config] + local_home_data,
        jvm_flags = _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path) + local_home_flags + [
            "-D%s=$(rlocationpath %s)" % (DEV_IDE_CONFIG_PATH_PROPERTY, ide_config),
            # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime,
            # and a dev launch has it only because `DevMainImpl` sets it from the project root it just built against.
            "-Didea.dev.project.root=$${BUILD_WORKSPACE_DIRECTORY}",
        ],
        env = env,
        add_opens = INTELLIJ_ADD_OPENS,
        args = program_args,
    )

def _launcher_runfile_path(ctx, file):
    path = file.short_path
    return path[3:] if path.startswith("../") else ctx.workspace_name + "/" + path

def _java_runfile_path(ctx, java_runtime):
    path = java_runtime.java_executable_runfiles_path
    if path.startswith("/"):
        return path
    return path[3:] if path.startswith("../") else ctx.workspace_name + "/" + path

def _intellij_dev_launcher_impl(ctx):
    java_runtime = ctx.toolchains["@bazel_tools//tools/jdk:runtime_toolchain_type"].java_runtime
    windows = ctx.target_platform_has_constraint(ctx.attr._windows[platform_common.ConstraintValueInfo])
    executable = ctx.actions.declare_file(ctx.label.name + (".exe" if windows else ""))
    ctx.actions.symlink(output = executable, target_file = ctx.executable._launcher, is_executable = True)

    # `$$` is a literal `$`, and the launcher expands `${NAME}` at launch, as the java stub's shell did.
    targets = ctx.attr.data + [ctx.attr.dist, ctx.attr.ide_config]
    jvm_flags = ctx.fragments.java.default_jvm_opts + [
        ctx.expand_make_variables("jvm_flags", ctx.expand_location(flag, targets), {})
        for flag in ctx.attr.jvm_flags
    ] + ["--add-opens=%s=ALL-UNNAMED" % package for package in ctx.attr.add_opens]
    manifest = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(manifest, json.encode({
        "version": 1,
        "java": _java_runfile_path(ctx, java_runtime),
        "ideConfig": _launcher_runfile_path(ctx, ctx.file.ide_config),
        "localHomeTool": _launcher_runfile_path(ctx, ctx.executable.local_home_tool),
        "beforeRun": _launcher_runfile_path(ctx, ctx.executable.before_run) if ctx.attr.before_run else "",
        "jvmFlags": jvm_flags,
        "home": ctx.attr.home,
    }))

    runfiles = ctx.runfiles(files = [executable, manifest, ctx.file.ide_config], transitive_files = java_runtime.files)
    for target in [ctx.attr.dist, ctx.attr.local_home_tool, ctx.attr._launcher] + ([ctx.attr.before_run] if ctx.attr.before_run else []) + ctx.attr.data:
        runfiles = runfiles.merge(ctx.runfiles(transitive_files = target[DefaultInfo].files)).merge(target[DefaultInfo].default_runfiles)
    return [
        DefaultInfo(executable = executable, files = depset([executable, manifest]), runfiles = runfiles),
        RunEnvironmentInfo(environment = ctx.attr.env),
    ]

intellij_dev_launcher = rule(
    doc = """Starts a composed dev distribution through the Go launcher, `bazel run //<package>:<name>`.

The launcher reads `<name>.launch.json`, which this rule writes, links the distribution's local home under
`$BUILD_WORKSPACE_DIRECTORY/<home>`, and replaces itself with the IDE's JVM in the workspace. On macOS it first makes
`out/dev-data` a link to the dev-data root of the checkout. It takes the java stub's wrapper options, so the IDE's Bazel
plugin can debug it. See `community/build/content-module-packer/dev-launcher`.""",
    implementation = _intellij_dev_launcher_impl,
    executable = True,
    fragments = ["java"],
    toolchains = ["@bazel_tools//tools/jdk:runtime_toolchain_type"],
    attrs = {
        "dist": attr.label(mandatory = True, doc = "The composed distribution, whose runfiles hold its home or its components."),
        "ide_config": attr.label(mandatory = True, allow_single_file = True, doc = "The `intellij_dev_dist_config` of `dist`."),
        "jvm_flags": attr.string_list(doc = "JVM flags; `$(location)` and make variables expand, and `${NAME}` expands at launch."),
        "add_opens": attr.string_list(doc = "Packages opened to the unnamed module, as `java_binary.add_opens`."),
        "env": attr.string_dict(doc = "Environment variables `bazel run` sets for the launcher."),
        "data": attr.label_list(allow_files = True, doc = "Extra runfiles of the launcher."),
        "local_home_tool": attr.label(mandatory = True, executable = True, cfg = "target", doc = "The collector whose `local-home` links a local home."),
        "before_run": attr.label(executable = True, cfg = "target", doc = "An executable the launcher runs in the workspace before the IDE, and fails with."),
        "home": attr.string(mandatory = True, doc = "The workspace-relative directory under which each launch links its home, under `out/dev-data`."),
        "_launcher": attr.label(default = Label("//build/content-module-packer/dev-launcher"), executable = True, cfg = "target"),
        "_windows": attr.label(default = Label("@platforms//os:windows")),
    },
)

def intellij_dev_launcher_binary(
        name,
        dist,
        ide_config,
        local_home_tool,
        jvm_flags = [],
        env = {},
        program_args = [],
        data = [],
        before_run_main_class = "",
        before_run_runtime_deps = [],
        visibility = None):
    """The Go launcher of a composed dev distribution, with the flags `intellij_dev_prebuilt_binary` gives its java stub.

    `dist` and `ide_config` are the distribution and its `intellij_dev_dist_config`. The home is linked under
    `out/dev-data/<name>/homes`, one directory per launch, beside the launcher's config and system directories. On macOS
    `out/dev-data` is a link to a directory outside the workspace. With `before_run_main_class`, a `java_binary`
    `<name>_before_run` runs that class over `before_run_runtime_deps` first.
    """
    tags = ["manual"]
    before_run = None
    if before_run_main_class:
        before_run = name + "_before_run"
        java_binary(
            name = before_run,
            main_class = before_run_main_class,
            runtime_deps = before_run_runtime_deps,
            tags = tags,
            visibility = ["//visibility:private"],
        )
    intellij_dev_launcher(
        name = name,
        visibility = visibility,
        tags = tags,
        dist = dist,
        ide_config = ide_config,
        local_home_tool = local_home_tool,
        before_run = before_run,
        # The IDE starts in the workspace, so a relative path in a flag resolves as it does for the run configuration.
        jvm_flags = _runtime_jvm_flags(name, jvm_flags, platform_prefix = None, config_path = None, system_path = None) + [
            # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime,
            # and a dev launch has it only because `DevMainImpl` sets it from the project root it just built against.
            "-Didea.dev.project.root=$${BUILD_WORKSPACE_DIRECTORY}",
        ],
        add_opens = INTELLIJ_ADD_OPENS,
        env = env,
        args = program_args,
        data = data,
        home = "out/dev-data/%s/homes" % name,
    )
