load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.diff.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.diff.impl.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/diff-api:intellij.platform.diff"],
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
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/diff-api:intellij.platform.diff[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core:intellij.platform.vcs.core[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.diff.tests",
    iml_files = ["intellij.platform.diff.tests.iml"],
    test_resources = ["tests/testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/relaxng:intellij.relaxng",
    ],
    test_srcs = ["tests/testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/diff-api:intellij.platform.diff"],
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
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/diff-api:intellij.platform.diff[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core:intellij.platform.vcs.core[module]",
        "//tools/idea/platform/diff-impl:intellij.platform.diff.impl[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:kotlin-test[test]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module, test]",
        "//tools/idea/platform/platform-tests:intellij.platform.tests[module, test]",
    ],
)
