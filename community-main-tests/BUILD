load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "community-main-tests",
    iml_files = ["community-main-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
        "//tools/vendor/intellij/cidr:doxygen",
        "//tools/idea/plugins/settings-repository",
        "//tools/idea/jps/javac-ref-scanner-8",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:community-main[module, test]",
        "//tools/adt/idea/android:android-tests[module, test]",
        "//tools/adt/idea/designer:designer-tests[module, test]",
    ],
)
