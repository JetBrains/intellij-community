"""Headful Compose Desktop UI tests driven by Spectre (JEWEL-1390).

Runs the JUnit Platform console launcher rather than `jps_test`, which forces headless mode and
pulls in the IntelliJ test runtime. See platform/jewel/docs/bazel-build-tips.md.
"""

load("@rules_java//java:defs.bzl", "java_test")
load("@rules_jvm//:jvm.bzl", "jvm_library")

SPECTRE_JVM_FLAGS = [
    "-Djava.awt.headless=false",
    # Keeps the test window out of the macOS Dock.
    "-Dapple.awt.UIElement=true",
    "-ea",
]

# Not `manual`: CI is meant to run these. `requires-display` is the opt-out handle.
SPECTRE_TAGS = [
    "requires-display",
    "no-sandbox",
    "local",
    "external",
]

def spectre_test(
        name,
        srcs,
        module_name,
        test_packages = ["org.jetbrains.jewel"],
        deps = [],
        runtime_deps = [],
        kotlinc_opts = None,
        jvm_flags = [],
        tags = [],
        size = "medium",
        data = [],
        visibility = None,
        **kwargs):
    """Compiles and runs a set of headful Spectre UI tests.

    Args:
        name: Target name. The compiled test sources land in `<name>_lib`.
        srcs: Kotlin sources of the tests.
        module_name: Kotlin module name for the compiled test sources.
        test_packages: Root packages to discover tests in. The default covers all of Jewel.
        deps: Compile dependencies. `//platform/jewel:spectre` is always added.
        runtime_deps: Extra runtime-only dependencies.
        kotlinc_opts: Label of the `create_kotlinc_options` target to compile with.
        jvm_flags: Extra JVM flags, appended after `SPECTRE_JVM_FLAGS`.
        tags: Extra tags, appended after `SPECTRE_TAGS`.
        size: Bazel test size. Headful tests start a real app, so the default is `medium`.
        data: Runfiles needed by the tests.
        visibility: Visibility of the test target.
        **kwargs: Passed through to `java_test`.
    """
    lib_name = name + "_lib"

    jvm_library(
        name = lib_name,
        testonly = True,
        srcs = srcs,
        kotlinc_opts = kotlinc_opts,
        module_name = module_name,
        visibility = ["//visibility:private"],
        deps = deps + ["//platform/jewel:spectre"],
        runtime_deps = runtime_deps,
    )

    java_test(
        name = name,
        main_class = "org.junit.platform.console.ConsoleLauncher",
        use_testrunner = False,
        # Both `--scan-classpath` forms silently find nothing under Bazel: bare scans only
        # directories, and `=<jar>` needs a runfiles symlink tree, which Windows lacks.
        args = [
            "execute",
            "--fail-if-no-tests",
            "--details=tree",
            "--disable-ansi-colors",
        ] + ["--select-package=" + test_package for test_package in test_packages],
        jvm_flags = SPECTRE_JVM_FLAGS + jvm_flags,
        # Bazel scrubs the test environment; without these AWT falls back to `:0.0` and fails.
        env_inherit = [
            "DISPLAY",
            "XAUTHORITY",
        ],
        runtime_deps = [
            ":" + lib_name,
            "//platform/jewel:spectre-junit-console",
        ],
        tags = SPECTRE_TAGS + tags,
        size = size,
        data = data,
        visibility = visibility,
        **kwargs
    )
