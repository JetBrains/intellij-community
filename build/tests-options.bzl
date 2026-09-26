load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@rules_java//java:defs.bzl", "java_test")

ADD_OPENS_FLAGS = ["--add-opens=" + pkg + "=ALL-UNNAMED" for pkg in INTELLIJ_ADD_OPENS]

# Mirrors COMMON_VM_OPTIONS in VmOptionsGenerator.kt
JAVA_TEST_FLAGS = [
    "-Didea.classpath.index.enabled=false",
    "-Djava.awt.headless=true",
    "-Djunit.jupiter.extensions.autodetection.enabled=true",
    "-Didea.force.use.core.classloader=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Didea.reset.classpath.from.manifest=true",
    "-Dintellij.build.use.compiled.classes=false",
    "-Djava.util.zip.use.nio.for.zip.file.access=true",
    "-ea",
]

JAVA_TEST_ARGS = [
]

TEST_FRAMEWORK_DEPS = [
    # TODO Review this list, most likely only tools-testsBootstrap is required
    # junit stuff should be propagated via runtime deps of testsBootstrap
    "@community//platform/testFramework/bootstrap:tools-testsBootstrap",
    "@community//platform/util:util-tests_test_lib",

    # Provide test engines to run actual tests
    # Junit 3/4 is also run by junit5 via junit vintage
    "@community//libraries/junit5-vintage",
    "@community//libraries/junit5-launcher",
]

# needed to avoid runtime duplications in jps_test of community/platform/util/BUILD.bazel
# as depset can't recognize that ":util-tests_test_lib" and "@community//platform/util:util-tests_test_lib" is the same lib
def _normalize_runtime_dep(dep):
    if ((dep == ":util-tests_test_lib" and native.package_name() == "platform/util") or
        dep in ["//platform/util:util-tests_test_lib", "@community//platform/util:util-tests_test_lib"]):
        return "@community//platform/util:util-tests_test_lib"
    return dep

def _classes_duration_data():
    repo = native.repository_name()
    module = native.module_name()

    # The top-level checkout has no explicit `module(name = "ultimate")`, so its main repository reports an empty module name.
    if repo == "@" and not module:
        return "//:tests/classes-duration"

    # When the top-level checkout builds `@community//...`, the classes-duration target still lives in the canonical main repo.
    if repo != "@" and module == "community":
        return "@@//:tests/classes-duration"

    # Standalone `community/bazel.cmd` runs with `repo == "@"` and `module == "community"`, so no extra data is added there.
    if repo == "@" and module == "community":
        return None

    fail("Unexpected repository/module combination for tests-options.bzl: repository_name=%r module_name=%r" % (repo, module))

def jps_test(name, jvm_flags = [], runtime_deps = [], args = [], data = [], tags = [], sandbox = False, env = {}, **kwargs):
    # Merge user-provided args with our default ones
    all_jvm_flags = JAVA_TEST_FLAGS + ADD_OPENS_FLAGS + jvm_flags
    all_args = JAVA_TEST_ARGS + args

    normalized_runtime_deps = [_normalize_runtime_dep(d) for d in runtime_deps]
    all_runtime_deps = depset(TEST_FRAMEWORK_DEPS + normalized_runtime_deps).to_list()

    # only what this macro contributes: the caller's `data` is concatenated at the end, so it may be
    # a select() - a target whose data depends on the platform, as the macOS-only dev-launch sets do
    all_data = []
    all_tags = list(tags)
    all_env = dict(env)

    # required for com.intellij.openapi.projectRoots.impl.JavaSdkImpl.internalJdkAnnotationsPath
    # almost all tests in monorepo need it, so we add it to all tests
    all_data.append("@community//java:mockJDK")

    # handled by com.intellij.tests.JUnit5BazelRunner.main
    all_env["JB_TEST_SANDBOX"] = str(sandbox)

    all_tags.append("jetbrains_test_runner")

    classes_duration_data = _classes_duration_data()
    if classes_duration_data != None:
        all_data.append(classes_duration_data)

    if sandbox:
        if "block-network" not in all_tags:
            all_tags.append("block-network")

        if "no-sandbox" in all_tags:
            fail("sandboxed (by sandbox parameter to jps_test) tests should not have no-sandbox tag")
    else:
        # so com.intellij.tests.JUnit5BazelRunner.guessBazelWorkspaceDir will find a real workspace root
        all_data.append("@community//:intellij.idea.community.main.iml")

        all_tags.append("external")

        if "no-sandbox" not in all_tags:
            all_tags.append("no-sandbox")

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
        tags = all_tags,
        data = data + all_data,
        env = all_env,
        use_testrunner = False,
        **kwargs
    )
