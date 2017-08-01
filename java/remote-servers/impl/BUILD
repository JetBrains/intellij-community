load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-java-impl",
    srcs = ["src"],
    iml_files = ["remote-servers-java-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/remote-servers/api:remote-servers-java-api[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/remote-servers/impl:remote-servers-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/openapi[module]",
    ],
)
