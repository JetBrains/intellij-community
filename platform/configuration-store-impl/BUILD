load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "configuration-store-impl",
    srcs = ["src"],
    iml_files = ["configuration-store-impl.iml"],
    package_prefixes = {
        "testSrc": "com.intellij.configurationStore",
        "src": "com.intellij.configurationStore",
    },
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea/.idea/libraries:xpp3-1.1.4-min",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
    ],
)
