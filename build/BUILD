load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "build",
    # do not sort: must match IML order
    srcs = [
        "scripts",
        "src",
    ],
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
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/jps/model-api:jps-model-api",
        "//tools/idea/jps/standalone-builder:jps-standalone-builder",
        "//tools/idea/build/lib:commons-cli-1.2",
        "//tools/idea/jps/antLayout:antlayout",
        "//tools/idea/platform/util",
        "//tools/idea/platform/build-scripts:platform-build-scripts",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/standalone-builder:jps-standalone-builder[module]",
        "//tools/idea/build/lib:commons-cli-1.2",
        "//tools/idea/jps/antLayout:antlayout[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/build-scripts:platform-build-scripts[module]",
        "//tools/idea/plugins/ui-designer/jps-plugin:ui-designer-jps-plugin[module]",
        "//tools/idea/plugins/groovy/jps-plugin:groovy-jps-plugin[module]",
        "//tools/idea/plugins/groovy/rt:groovy_rt[module]",
    ],
)
