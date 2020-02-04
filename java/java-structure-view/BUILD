load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.structureView",
    srcs = ["src"],
    iml_files = ["intellij.java.structureView.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/structure-view-impl:intellij.platform.structureView.impl"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structure-view-impl:intellij.platform.structureView.impl[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
