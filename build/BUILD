load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.idea.community.build",
    # do not sort: must match IML order
    srcs = [
        "scripts",
        "groovy",
    ],
    bundle_data = glob([
        "gant.xml",
        "scripts/*",
        "groovy/**/*",
        "conf/**/*",
        "*.txt",
    ]),
    iml_files = ["intellij.idea.community.build.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/idea/platform/build-scripts:intellij.platform.buildScripts"],
    # do not sort: must match IML order
    deps = ["//tools/idea/platform/build-scripts:intellij.platform.buildScripts[module]"],
)
