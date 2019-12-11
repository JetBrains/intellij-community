load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.credentialStore",
    srcs = ["src"],
    iml_files = ["intellij.platform.credentialStore.iml"],
    package_prefixes = {
        "test": "com.intellij.credentialStore",
        "src": "com.intellij.credentialStore",
    },
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:bouncy-castle-provider",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/.idea/libraries:snakeyaml",
        "//prebuilts/tools/common/m2/repository/com/github/hypfvieh/dbus-java/3.0.2:jar",
        "//prebuilts/tools/common/m2/repository/com/github/hypfvieh/java-utils/1.0.5:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.25:jar",
    ],
)
