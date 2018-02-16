load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "build",
    # do not sort: must match IML order
    srcs = ["scripts"],
    bundle_data = glob([
        "gant.xml",
        "scripts/*",
        "groovy/**/*",
        "conf/**/*",
        "*.txt",
    ]),
    iml_files = ["build.iml"],
    resources = ["groovy"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-api:jps-model-api",
        "//tools/idea/jps/standalone-builder:jps-standalone-builder",
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.2:jar",
        "//tools/idea/jps/antLayout:antlayout",
        "//tools/idea/platform/util",
        "//tools/idea/platform/build-scripts:platform-build-scripts",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/plugins/ui-designer/jps-plugin:ui-designer-jps-plugin",
        "//tools/idea/plugins/groovy/jps-plugin:groovy-jps-plugin",
        "//tools/idea/plugins/groovy/rt:groovy_rt",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/java-runtime",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/standalone-builder:jps-standalone-builder[module]",
        "//prebuilts/tools/common/m2/repository/commons-cli/commons-cli/1.2:jar",
        "//tools/idea/jps/antLayout:antlayout[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/build-scripts:platform-build-scripts[module]",
    ],
)

iml_module(
    name = "dependencies",
    iml_files = ["dependencies/dependencies.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "prebuilts",
    srcs = ["dependencies/gradle.properties"],
    visibility = ["//visibility:public"],
)
