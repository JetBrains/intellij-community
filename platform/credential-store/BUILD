load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "credential-store",
    srcs = ["src"],
    iml_files = ["credential-store.iml"],
    package_prefixes = {
        "test": "com.intellij.credentialStore",
        "src": "com.intellij.credentialStore",
    },
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:bouncy-castle-provider",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
    ],
)
