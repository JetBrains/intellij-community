load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.jsp.spi",
    srcs = ["src"],
    iml_files = ["intellij.jsp.spi.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/jsp-openapi:intellij.jsp",
        "//tools/idea/java/openapi:intellij.java",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/jsp-openapi:intellij.jsp[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/java/jsp-base-openapi:intellij.jsp.base[module]",
    ],
)
