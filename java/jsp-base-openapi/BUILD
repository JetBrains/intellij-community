load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.jsp.base",
    srcs = ["src"],
    iml_files = ["intellij.jsp.base.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/openapi:intellij.xml"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
    ],
)
