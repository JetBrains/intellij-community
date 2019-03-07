load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java",
    srcs = ["src"],
    iml_files = ["intellij.java.iml"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
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
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/xml/openapi:intellij.xml",
        "//tools/idea/platform/platform-api:intellij.platform.ide",
        "//tools/idea/platform/lang-api:intellij.platform.lang",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs",
        "//tools/idea/resources-en:intellij.java.resources.en",
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing",
        "//tools/idea/java/java-analysis-api:intellij.java.analysis",
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
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/icons:intellij.platform.icons[module]",
        "//tools/idea/.idea/libraries:microba",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/resources-en:intellij.java.resources.en[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/idea/java/java-analysis-api:intellij.java.analysis[module]",
    ],
)
