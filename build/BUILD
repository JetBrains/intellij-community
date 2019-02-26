load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.idea.community.build",
    # do not sort: must match IML order
    srcs = [
        "scripts",
        "groovy",
    ],
    bundle_data = glob([
        "gant.xml",
        "scripts/*",
        "groovy/**/*",
        "conf/**/*",
        "*.txt",
    ]),
    iml_files = ["intellij.idea.community.build.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-api:intellij.platform.jps.model",
        "//tools/idea/jps/standalone-builder:intellij.tools.jps.build.standalone",
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.2:jar",
        "//tools/idea/jps/antLayout:intellij.tools.jps.antTasks",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/build-scripts:intellij.platform.buildScripts",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/plugins/ui-designer/jps-plugin:intellij.java.guiForms.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/groovy/rt:intellij.groovy.rt",
        "//tools/idea/.idea/libraries:netty-codec-http",
        "//tools/idea/.idea/libraries:netty-handler",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/java-runtime:intellij.java.rt",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/jps/standalone-builder:intellij.tools.jps.build.standalone[module]",
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.2:jar",
        "//tools/idea/jps/antLayout:intellij.tools.jps.antTasks[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/build-scripts:intellij.platform.buildScripts[module]",
    ],
)
