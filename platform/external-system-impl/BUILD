load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.externalSystem.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.externalSystem.impl.iml"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module]",
        "//tools/idea/.idea/libraries:kotlin-reflect",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer[module]",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer.annotations[module]",
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.externalSystem.tests",
    iml_files = ["intellij.platform.externalSystem.tests.iml"],
    test_resources = ["testResources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/ant/jps-plugin:intellij.ant.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/ByteCodeViewer:intellij.java.byteCodeViewer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module, test]",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl[module, test]",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module, test]",
        "//tools/idea:intellij.java.compiler.tests[module, test]",
        "//tools/idea/.idea/libraries:kotlin-reflect",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
