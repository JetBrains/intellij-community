"""Headful in-IDE end-to-end tests for the Jewel IntelliJ Platform bridge (JEWEL-1397).

These build a dev IDE from sources, launch it, and drive its Jewel Compose content with Spectre,
attached over the JDK Attach API. That is the only way to exercise `JBPopupRenderer`, which builds a
real `JBPopup` through `JBPopupFactory` and so needs a running application and a display.

Out-of-process on purpose. The test JVM stays headless, as `jps_test` intends, and never draws
anything: it talks to the IDE over the agent's IPC socket. Only the IDE needs a display. That is also
what lets the same scenarios run against the standalone sample under Spectre's in-process automator,
so the two hosts cannot drift apart.

Nothing here reaches the IDE's own classpath. The agent's inject bootstrap carries what it needs into
the target at attach time, so `spectre-core` never ships in a dev IDE, let alone a published artifact.

See platform/jewel/docs/e2e-tests.md.
"""

load("@community//build:tests-options.bzl", "jps_test")
load("@jps_dynamic_deps_community//:targets.bzl", "ALL_COMMUNITY_TARGETS", "BAZEL_TARGETS_JSON_COMMUNITY")
load("@rules_jvm//:jvm.bzl", "jvm_library")

SPECTRE_AGENT_RUNTIME_JAR = "//platform/jewel:spectre-agent-runtime-jar"

# The test JVM is headless; the IDE it launches is not, so the agent still needs a real display.
# Not `manual`: CI is meant to run these wherever one exists, exactly like the Spectre lane.
BRIDGE_E2E_TAGS = [
    "requires-display",
    "no-sandbox",
    "local",
    # The dev build server writes into a shared `out/dev-run` directory keyed by the module set, so two of these
    # running at once fight over the same output and fail with FileAlreadyExistsException. Run them one at a time.
    "exclusive",
]

def bridge_e2e_test(
        name,
        srcs,
        module_name,
        deps = [],
        runtime_deps = [],
        kotlinc_opts = None,
        jvm_flags = [],
        tags = [],
        data = [],
        # Building a dev IDE and launching it is minutes, not seconds.
        timeout = "eternal",
        visibility = None,
        **kwargs):
    """Compiles and runs a set of headful in-IDE bridge tests.

    Args:
        name: Target name. The compiled test sources land in `<name>_lib`.
        srcs: Kotlin sources of the tests.
        module_name: Kotlin module name for the compiled test sources.
        deps: Compile dependencies, on top of the starter, driver and Spectre attach ones added here.
        runtime_deps: Extra runtime-only dependencies.
        kotlinc_opts: Label of the `create_kotlinc_options` target to compile with.
        jvm_flags: Extra JVM flags for the test JVM.
        tags: Extra tags, appended after `BRIDGE_E2E_TAGS`.
        data: Extra runfiles, on top of the community target graph.
        timeout: Bazel test timeout.
        visibility: Visibility of the test target.
        **kwargs: Passed through to `jps_test`.
    """
    lib_name = name + "_lib"

    jvm_library(
        name = lib_name,
        testonly = True,
        srcs = srcs,
        kotlinc_opts = kotlinc_opts,
        module_name = module_name,
        visibility = ["//visibility:private"],
        deps = deps + [
            "@lib//:kotlin-stdlib",
            "//libraries/junit5",
            "//platform/bazel-runfiles:bazel-runfiles",
            "//platform/jewel:spectre-attach",
            "//platform/remote-driver/client",
            "//platform/remote-driver/test-sdk:driver-sdk",
            "//tools/intellij.tools.ide.starter:ide-starter",
            "//tools/intellij.tools.ide.starter.driver:ide-starter-driver",
            "//tools/intellij.tools.ide.starter.product.idea.community:ide-starter-product-idea-community",
        ],
        runtime_deps = runtime_deps + [
            # Without this on the classpath `DevBuildServerRunner.isDevBuildSupported()` is false, and
            # the starter quietly downloads a release IDE instead of building one from sources. The
            # tests then run against an IDE that has none of the local changes, and fail confusingly.
            "//tools/intellij.tools.ide.starter.build.server:ide-starter-build-server",
        ],
    )

    jps_test(
        name = name,
        # The IDE under test is built from sources at run time, so the whole community target graph
        # has to be present as runfiles, along with the manifest mapping modules to those targets.
        data = data + ALL_COMMUNITY_TARGETS + [
            BAZEL_TARGETS_JSON_COMMUNITY,
            SPECTRE_AGENT_RUNTIME_JAR,
        ],
        jvm_flags = jvm_flags + [
            "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % BAZEL_TARGETS_JSON_COMMUNITY,
            # Handed over as a runfiles path rather than found by scanning `java.class.path`: on Windows
            # Bazel collapses a long classpath into a single manifest-only jar, so no individual entry for
            # the agent is visible there.
            "-Djewel.spectre.agentRuntimeJar=$(rlocationpath %s)" % SPECTRE_AGENT_RUNTIME_JAR,
        ],
        # Point the runner straight at the test jar. Otherwise it derives the test binary's directory from
        # SELF_LOCATION, which Bazel's Windows test wrapper does not set, and discovery finds nothing there.
        env = {"JB_TEST_JAR": lib_name + ".jar"},
        # Bazel scrubs the test environment, and the IDE needs the display. Without these the IDE Starter wraps the
        # IDE in an `xvfb-run` of its own, and Spectre attaches to that wrapper instead of the IDE.
        env_inherit = [
            "DISPLAY",
            "XAUTHORITY",
        ],
        tags = BRIDGE_E2E_TAGS + tags,
        timeout = timeout,
        visibility = visibility,
        runtime_deps = [":" + lib_name],
        **kwargs
    )
