load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "extensions",
    srcs = ["src"],
    iml_files = ["extensions.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Xerces[test]",
        "//tools/idea/.idea/libraries:hamcrest[test]",
    ],
)
