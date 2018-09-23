load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.configurationStore.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.configurationStore.impl.iml"],
    package_prefixes = {
        "testSrc": "com.intellij.configurationStore",
        "src": "com.intellij.configurationStore",
    },
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:lz4-java",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
    ],
)
