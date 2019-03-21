load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.analysis.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.analysis.impl.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/resources-en:intellij.java.resources.en[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/editor-ui-ex:intellij.platform.editor.ex[module]",
        "//tools/idea/platform/indexing-impl:intellij.platform.indexing.impl[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/idea/.idea/libraries:HdrHistogram",
        "//tools/idea/.idea/libraries:protobuf",
    ],
)
