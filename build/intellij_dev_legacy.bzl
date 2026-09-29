"""The legacy dev-mode test macro: it assembles the product in the test JVM through the build scripts.

The dev-mode tests are the last Bazel path that runs `JUnitDevMainKt`. Every dev launcher runs from a split dev
distribution and the launcher in `intellij_dev.bzl`.
"""

load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@rules_java//java:defs.bzl", "java_test")
load(
    ":dev_launch_dependencies.bzl",
    "preloaded_downloads_data",
    "preloaded_downloads_flag",
    "preloaded_downloads_manifest_data",
    "preloaded_downloads_only_flag",
)

# `JUnitDevMainKt`, which assembles the product in process with the build scripts.
_LEGACY_LAUNCHER_MODULE = "@community//platform/bootstrap/dev-legacy"

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
        main_class_module = _LEGACY_LAUNCHER_MODULE):
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
        preloaded_download_repos: the download repositories whose archives the test gets as runfiles.
        preloaded_downloads_exhaustive_on: the platforms where those archives are the whole download set, so an
            undeclared URL is an error and not a download.
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
