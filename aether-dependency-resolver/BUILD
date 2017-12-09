load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "aether-dependency-resolver",
    srcs = ["src"],
    iml_files = ["aether-dependency-resolver.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/lib:aether-1.1.0-all",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/lib:aether-1.1.0-all",
        "//tools/idea/lib:maven-aether-provider-3.3.9-all",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/platform/testFramework[module, test]",
    ],
)
