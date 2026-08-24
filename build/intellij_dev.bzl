"""Macros for IntelliJ-based IDE development builds."""

load("@rules_java//java:defs.bzl", "java_binary")
load(
    ":dev_launch_dependencies.bzl",
    "preloaded_downloads_data",
    "preloaded_downloads_flag",
    "preloaded_downloads_manifest_data",
    "preloaded_downloads_only_flag",
)

INTELLIJ_ADD_OPENS = [
    "java.base/java.io",
    "java.base/java.lang",
    "java.base/java.lang.ref",
    "java.base/java.lang.reflect",
    "java.base/java.net",
    "java.base/java.nio",
    "java.base/java.nio.charset",
    "java.base/java.text",
    "java.base/java.time",
    "java.base/java.util",
    "java.base/java.util.concurrent",
    "java.base/java.util.concurrent.atomic",
    "java.base/java.util.concurrent.locks",
    "java.base/jdk.internal.ref",
    "java.base/jdk.internal.vm",
    "java.base/sun.net.dns",
    "java.base/sun.nio",
    "java.base/sun.nio.ch",
    "java.base/sun.nio.fs",
    "java.base/sun.security.ssl",
    "java.base/sun.security.util",
    "java.desktop/com.apple.eawt",
    "java.desktop/com.apple.eawt.event",
    "java.desktop/com.apple.laf",
    "java.desktop/com.sun.java.swing",
    "java.desktop/com.sun.java.swing.plaf.gtk",
    "java.desktop/java.awt",
    "java.desktop/java.awt.dnd.peer",
    "java.desktop/java.awt.event",
    "java.desktop/java.awt.font",
    "java.desktop/java.awt.image",
    "java.desktop/java.awt.peer",
    "java.desktop/javax.swing",
    "java.desktop/javax.swing.plaf.basic",
    "java.desktop/javax.swing.text",
    "java.desktop/javax.swing.text.html",
    "java.desktop/javax.swing.text.html.parser",
    "java.desktop/sun.awt",
    "java.desktop/sun.awt.X11",
    "java.desktop/sun.awt.datatransfer",
    "java.desktop/sun.awt.image",
    "java.desktop/sun.awt.windows",
    "java.desktop/sun.font",
    "java.desktop/sun.java2d",
    "java.desktop/sun.lwawt",
    "java.desktop/sun.lwawt.macosx",
    "java.desktop/sun.swing",
    "java.desktop/sun.swing.text",
    "java.management/sun.management",
    "jdk.attach/sun.tools.attach",
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.internal.jvmstat/sun.jvmstat.monitor",
    "jdk.jdi/com.sun.tools.jdi",
]

# Names the prepared distribution for whoever consumes one - `PreBuiltDevMain` when it is a launcher, the IDE Starter's
# prebuilt dev-build runner when it is a test. Keep in sync with `DevIdeConfig.CONFIG_PATH_PROPERTY`, which is where the
# reading side of this contract lives.
DEV_IDE_CONFIG_PATH_PROPERTY = "idea.ide.config.path"

def intellij_dev_dist_config(name, dist, visibility = None):
    """A single-file label for an assembled dev distribution's config file, for `$(rlocationpath ...)`.

    That expansion takes a label naming exactly one file, which a dist target - two outputs, one of them declared rather
    than predeclared - is not. Its `ide_config` output group is how it gets one.

    A consumer declares both this and the dist itself in `data`, and they must stay siblings in the runfiles tree: the
    config names the home relatively, so that the pair survives being read from a different path than it was written to.
    """
    native.filegroup(
        name = name,
        srcs = [dist],
        output_group = "ide_config",
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
    all_jvm_flags = DEFAULT_JVM_FLAGS + jvm_flags

    if platform_prefix:
        all_jvm_flags = all_jvm_flags + ["-Didea.platform.prefix=" + platform_prefix]

    # Use provided paths or defaults based on target name
    effective_config_path = config_path if config_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/config"
    effective_system_path = system_path if system_path else "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/system"

    return all_jvm_flags + [
        "-Didea.config.path=" + effective_config_path,
        "-Didea.system.path=" + effective_system_path,
    ]

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

    # Allow to reset classpath from META-INF/MANIFEST.MF if classpath .jar due to classpath length limitations on Windows
    # https://github.com/bazelbuild/bazel/blob/93cde47ab3236b3b7124b41824f843f3659064de/src/tools/launcher/java_launcher.cc#L385
    all_jvm_flags += select({
        "@bazel_tools//src/conditions:windows": ["-Didea.reset.classpath.from.manifest=true"],
        "//conditions:default": [],
    })

    if additional_modules:
        all_jvm_flags = all_jvm_flags + ["-Dadditional.modules=\"" + additional_modules + "\""]

    main_class = "org.jetbrains.intellij.build.devServer.DevMainKt"
    runtime_deps = ["@community//platform/bootstrap/dev"]
    if before_run_main_class:
        main_class = "org.jetbrains.intellij.build.devServer.BeforeRunDevMain"
        runtime_deps = runtime_deps + before_run_runtime_deps
        all_jvm_flags = all_jvm_flags + [
            "-Dintellij.build.dev.server.before.run.main.class=" + before_run_main_class,
        ]

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
        visibility = None):
    """Launches an already-assembled dev distribution, instead of assembling one first.

    The counterpart of [intellij_dev_binary]: same runtime vocabulary, but the layout was computed once by the dist's
    action rather than on every `bazel run`. So none of the build's inputs appear here - no module jars, no
    `bazel-targets.json`, no preloaded downloads, and no `additional.modules`, which the distribution was assembled with
    and therefore already contains. What is left is the distribution itself, and nothing in it is product-specific.
    """
    ide_config = name + "_ide_config"

    intellij_dev_dist_config(name = ide_config, dist = dist, visibility = ["//visibility:private"])

    java_binary(
        name = name,
        visibility = visibility,
        runtime_deps = ["@community//platform/bootstrap/dev"],
        main_class = "org.jetbrains.intellij.build.devServer.PreBuiltDevMain",
        # Both outputs, and they must stay siblings: the config names the home relatively, so that the pair survives
        # being read from a different path than it was written to.
        data = [dist, ide_config],
        jvm_flags = _runtime_jvm_flags(name, jvm_flags, platform_prefix, config_path, system_path) + [
            "-D%s=$(rlocationpath %s)" % (DEV_IDE_CONFIG_PATH_PROPERTY, ide_config),
            # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime,
            # and a dev launch has it only because `DevMainImpl` sets it from the project root it just built against.
            "-Didea.dev.project.root=$${BUILD_WORKSPACE_DIRECTORY}",
        ],
        env = env,
        add_opens = INTELLIJ_ADD_OPENS,
        args = program_args,
    )
