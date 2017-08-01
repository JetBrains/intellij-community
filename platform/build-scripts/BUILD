load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "platform-build-scripts",
    srcs = ["icons/src"],
    iml_files = ["platform-build-scripts.iml"],
    resources = ["groovy"],
    tags = ["managed"],
    test_srcs = ["icons/tests"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/jps/model-api:jps-model-api",
        "//tools/idea/jps/standalone-builder:jps-standalone-builder",
        "//tools/idea/platform/util",
        "//tools/idea/build/lib:gant/lib/gant-1.9.11_groovy-2.3.0",
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
        "//tools/idea/platform/util[module]",
        "//tools/idea/plugins/ui-designer/jps-plugin:ui-designer-jps-plugin[module]",
        "//tools/idea/plugins/groovy/jps-plugin:groovy-jps-plugin[module]",
        "//tools/idea/plugins/groovy/rt:groovy_rt[module]",
        "//tools/idea/build/lib:gant/lib/gant-1.9.11_groovy-2.3.0",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:commons-compress",
    ],
)

filegroup(
    name = "ant-build",
    srcs = glob(["groovy/**/*"]),
    visibility = ["//visibility:public"],
)
