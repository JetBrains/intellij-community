load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.lang",
    srcs = ["src"],
    iml_files = ["intellij.platform.lang.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/platform-api:intellij.platform.ide",
        "//tools/idea/platform/lvcs-api:intellij.platform.lvcs",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lvcs-api:intellij.platform.lvcs[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:hamcrest[test]",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
