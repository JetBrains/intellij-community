load("@rules_java//java:defs.bzl", "java_test")

PKGS = [
    "java.base/java.io",
    "java.base/java.lang",
    "java.base/java.lang.reflect",
    "java.base/java.net",
    "java.base/java.nio",
    "java.base/java.nio.charset",
    "java.base/java.text",
    "java.base/java.time",
    "java.base/java.util",
    "java.base/java.util.concurrent",
    "java.base/java.util.concurrent.atomic",
    "java.base/jdk.internal.vm",
    "java.base/sun.nio.ch",
    "java.base/sun.nio.fs",
    "java.base/sun.security.ssl",
    "java.base/sun.security.util",
    "java.desktop/com.apple.eawt",
    "java.desktop/com.apple.eawt.event",
    "java.desktop/com.apple.laf",
    "java.desktop/java.awt",
    "java.desktop/java.awt.dnd.peer",
    "java.desktop/java.awt.event",
    "java.desktop/java.awt.image",
    "java.desktop/java.awt.peer",
    "java.desktop/java.awt.font",
    "java.desktop/javax.swing",
    "java.desktop/javax.swing.plaf.basic",
    "java.desktop/javax.swing.text.html",
    "java.desktop/sun.awt.datatransfer",
    "java.desktop/sun.awt.image",
    "java.desktop/sun.awt",
    "java.desktop/sun.font",
    "java.desktop/sun.java2d",
    "java.desktop/sun.lwawt",
    "java.desktop/sun.lwawt.macosx",
    "java.desktop/sun.swing",
    "jdk.attach/sun.tools.attach",
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.internal.jvmstat/sun.jvmstat.monitor",
    "jdk.jdi/com.sun.tools.jdi",
]

ADD_OPENS_FLAGS = ["--add-opens=" + pkg + "=ALL-UNNAMED" for pkg in PKGS]

JAVA_TEST_FLAGS = [
    "-Didea.classpath.index.enabled=false",
    "-Djava.awt.headless=true",
    "-Djunit.jupiter.extensions.autodetection.enabled=true",
    "-Didea.force.use.core.classloader=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Didea.reset.classpath.from.manifest=true",
    "-Dintellij.build.use.compiled.classes=false",
]

JAVA_TEST_ARGS = [
]

TEST_FRAMEWORK_DEPS = [
  # TODO Review this list, most likely only tools-testsBootstrap is required
  # junit stuff should be propagated via runtime deps of testsBootstrap

  "@community//platform/testFramework/bootstrap:tools-testsBootstrap",
  "@community//platform/util:util-tests_test_lib",

  "@lib//:junit5Vintage",
  "@lib//:junit5Vintage-provided",
  "@lib//:junit4",
  "@lib//:junit4-provided",
]

# needed to avoid runtime duplications in jps_test of community/platform/util/BUILD.bazel
# as depset can't recognize that ":util-tests_test_lib" and "@community//platform/util:util-tests_test_lib" is the same lib
def _normalize_runtime_dep(dep):
    if dep in [
        ":util-tests_test_lib",
        "//platform/util:util-tests_test_lib",
        "@community//platform/util:util-tests_test_lib",
    ]:
        return "@community//platform/util:util-tests_test_lib"
    return dep

def jps_test(name, jvm_flags = [], runtime_deps = [], args = [], data = [], **kwargs):
    # Merge user-provided args with our default ones
    all_jvm_flags = JAVA_TEST_FLAGS + ADD_OPENS_FLAGS + jvm_flags
    all_args = JAVA_TEST_ARGS + args

    normalized_runtime_deps = [_normalize_runtime_dep(d) for d in runtime_deps]
    all_runtime_deps = depset(TEST_FRAMEWORK_DEPS + normalized_runtime_deps).to_list()

    # https://bazel.build/reference/be/java#java_test
    # https://bazel.build/reference/be/common-definitions#common-attributes-tests
    java_test(
        name = name,
        main_class = "com.intellij.tests.JUnit5BazelRunner",
        jvm_flags = all_jvm_flags,
        args = all_args,
        runtime_deps = all_runtime_deps,
        # maximum available test size, do not enforce more precise limits for now
        # settings size also sets test timeout to 1 hours
        # which is also a reasonable tests timeout for current state of things
        size = "enormous",
        data = [
            # so com.intellij.tests.JUnit5BazelRunner.guessBazelWorkspaceDir will find a real workspace root
            "@community//:intellij.idea.community.main.iml",
            # required for com.intellij.openapi.projectRoots.impl.JavaSdkImpl.internalJdkAnnotationsPath
            "@community//java:mockJDK",
        ] + data,
        use_testrunner = False,
        **kwargs
    )
