load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "core-api",
    srcs = ["src"],
    iml_files = ["core-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/util",
        "//tools/idea/platform/extensions",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/platform-resources-en",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/platform-resources-en[module]",
        "//tools/idea/.idea/libraries:automaton",
        "//tools/idea/.idea/libraries:CGLIB",
    ],
)
