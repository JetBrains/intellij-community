load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "built-in-server",
    srcs = ["src"],
    iml_files = ["built-in-server.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/built-in-server-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/.idea/libraries:commons-imaging",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/built-in-server-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

iml_module(
    name = "built-in-server-tests",
    iml_files = ["built-in-server-tests.iml"],
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
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/.idea/libraries:commons-imaging",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/built-in-server-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/platform/built-in-server[module]",
    ],
)
