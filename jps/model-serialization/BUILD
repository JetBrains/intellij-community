load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-model-serialization",
    srcs = ["src"],
    iml_files = ["jps-model-serialization.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:JDOM"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)

iml_module(
    name = "jps-serialization-tests",
    iml_files = ["jps-serialization-tests.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/jps/model-impl:jps-model-tests"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/jps/model-impl:jps-model-tests[module, test]",
    ],
)
