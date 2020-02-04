load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.jsp.base",
    srcs = ["src"],
    iml_files = ["intellij.jsp.base.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/openapi:intellij.xml"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
    ],
)
