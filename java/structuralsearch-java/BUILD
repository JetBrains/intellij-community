load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch-java",
    srcs = ["src"],
    iml_files = ["structuralsearch-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
    ],
)
