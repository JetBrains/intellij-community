"""Macros for IntelliJ-based IDE development builds."""

load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@rules_java//java:defs.bzl", "java_binary", "java_test")
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
        before_run_runtime_deps = []):
    """Launches a built distribution or a linked local home without packaging it.

    The distribution declares its product and additional modules.
    When it supplies local metadata, local_home_tool prepares a temporary home from its component runfiles.
    `data` is the launcher's extra runfiles, on top of the distribution and its config.
    With `before_run_main_class`, `BeforeRunDevMain` runs that class over `before_run_runtime_deps` first, then
    `PreBuiltDevMain`, as `intellij_dev_binary` does before `DevMainKt`.
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
`$BUILD_WORKSPACE_DIRECTORY/<home>`, and replaces itself with the IDE's JVM in the workspace. It takes the java stub's
wrapper options, so the IDE's Bazel plugin can debug it. See `community/build/content-module-packer/dev-launcher`.""",
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
        "home": attr.string(mandatory = True, doc = "The workspace-relative directory under which each launch links its home."),
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
    `out/dev-data/<name>/homes`, one directory per launch, beside the launcher's config and system directories. With
    `before_run_main_class`, a `java_binary` `<name>_before_run` runs that class over `before_run_runtime_deps` first.
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

def intellij_dev_test(
        name,
        visibility,
        data,
        jvm_flags,
        env,
        platform_prefix,
        bazel_targets_json,
        additional_modules,
        test_module,
        entry_point_module,
        preloaded_download_repos,
        preloaded_downloads_exhaustive_on,
        sandbox = False,
        tags = [],
        add_opens = [],
        main_class = "org.jetbrains.intellij.build.devServer.JUnitDevMainKt",
        main_class_module = "@community//platform/bootstrap/dev"):
    """Tests inside a dev build of a product, with production class loaders: the Bazel form of a `unitTesting.runners`
    entry in the root `intellij.yaml`.

    `main_class` assembles the product from the jars in `data` and starts `JUnit5BazelRunner` inside it, from the class
    loader of `entry_point_module`. The test's own classpath is the runtime classpath of `main_class_module` only: the
    tests, the test framework and the runner come from the assembled product, as in the IDE.

    Args:
        name: the target name.
        visibility: the target's visibility.
        data: the jars the product is assembled from.
        jvm_flags: the runner's `jvmArgs` beyond the ones this macro sets.
        env: environment variables of the test.
        platform_prefix: `-Didea.platform.prefix`.
        bazel_targets_json: the `bazel-targets.json` of the repository.
        additional_modules: `-Dadditional.modules`: the test plugin and the non-bundled plugins the tests need.
        test_module: the JPS module whose tests run; its jar inside the assembled product is scanned for tests.
        entry_point_module: the content module whose class loader the tests run in; empty means `test_module`.
        preloaded_download_repos: see `intellij_dev_binary`.
        preloaded_downloads_exhaustive_on: see `intellij_dev_binary`.
        sandbox: as the `jps_test` parameter of the same name.
        tags: extra tags.
        add_opens: packages to open beyond `INTELLIJ_ADD_OPENS`, as `module/package`.
        main_class: the runner's `mainClass`.
        main_class_module: the label of the runner's `mainClassModule`.
    """
    all_jvm_flags = [
        # as in JAVA_TEST_FLAGS of tests-options.bzl, except the flags that force the flat classpath
        "-Djava.awt.headless=true",
        "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
        "-Didea.reset.classpath.from.manifest=true",
        "-Djava.util.zip.use.nio.for.zip.file.access=true",
        "-ea",
        "-Didea.platform.prefix=" + platform_prefix,
        "-Dadditional.modules=" + ",".join(additional_modules),
        "-Didea.dev.build.test.entry.point.module=" + (entry_point_module or test_module),
        "-Didea.dev.build.test.entry.point.class=com.intellij.tests.JUnit5BazelRunner",
        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % bazel_targets_json,
    ] + jvm_flags

    all_jvm_flags = all_jvm_flags + preloaded_downloads_flag(preloaded_download_repos)
    if preloaded_downloads_exhaustive_on:
        all_jvm_flags = all_jvm_flags + preloaded_downloads_only_flag(preloaded_downloads_exhaustive_on)
    preloaded_data = (
        preloaded_downloads_data(preloaded_download_repos) +
        preloaded_downloads_manifest_data(preloaded_download_repos)
    )

    all_tags = ["jetbrains_test_runner"] + tags
    if sandbox:
        if "block-network" not in all_tags:
            all_tags.append("block-network")
        if "no-sandbox" in all_tags:
            fail("sandboxed (by sandbox parameter to intellij_dev_test) tests should not have no-sandbox tag")
    else:
        all_tags.append("external")
        if "no-sandbox" not in all_tags:
            all_tags.append("no-sandbox")
        all_tags.append("exclusive-if-local")

    java_test(
        name = name,
        visibility = visibility,
        main_class = main_class,
        runtime_deps = [main_class_module],
        data = data + [bazel_targets_json] + preloaded_data,
        jvm_flags = all_jvm_flags,
        add_opens = INTELLIJ_ADD_OPENS + add_opens,
        env = {
            "JB_TEST_SANDBOX": str(sandbox),
            "JB_TEST_JAR": test_module + ".jar",
        } | env,
        size = "enormous",
        tags = all_tags,
        use_testrunner = False,
    )
