load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.buildScripts",
    srcs = ["groovy"],
    iml_files = ["intellij.platform.buildScripts.iml"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:jps-build-script-dependencies-bootstrap",
        "//tools/idea/.idea/libraries:commons-cli",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:jps-build-script-dependencies-bootstrap",
        "//tools/idea/.idea/libraries:commons-cli",
    ],
)

filegroup(
    name = "scripts",
    srcs = glob([
        "groovy/**/*",
        "resources/**/*",
    ]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.images.build",
    srcs = ["icons/src"],
    iml_files = ["icons/intellij.platform.images.build.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["icons/tests"],
    test_tags = ["manual"],  # fails after IDEA 181.2784.17 merge
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:commons-imaging",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/usageView:intellij.platform.usageView",
        "//tools/idea/platform/execution-impl:intellij.platform.execution.impl",
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel/ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/jps/model-impl:intellij.platform.jps.model.impl[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.tools.jps.buildScriptDependencies",
    iml_files = ["intellij.tools.jps.buildScriptDependencies.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/201.11:jar",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/jps/standalone-builder:intellij.tools.jps.build.standalone",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/groovy/rt:intellij.groovy.rt",
        "//tools/idea/plugins/ui-designer/jps-plugin:intellij.java.guiForms.jps",
        "//tools/idea/jps/antLayout:intellij.tools.jps.antTasks",
        "//tools/idea/platform/statistics/config:intellij.platform.statistics.config",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-ant",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy-nio/2.5.11:jar",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy-cli-commons/2.5.11:jar",
        "//tools/idea/.idea/libraries:gant-groovy",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/java-runtime:intellij.java.rt",
    ],
)
