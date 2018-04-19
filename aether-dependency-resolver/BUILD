load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.aetherDependencyResolver",
    srcs = ["src"],
    iml_files = ["intellij.java.aetherDependencyResolver.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
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
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/lib:aether-1.1.0-all",
        "//tools/idea/lib:maven-aether-provider-3.3.9-all",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
    ],
)
