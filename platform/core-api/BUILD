load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.core",
    srcs = ["src"],
    bundle_data = [
        "//tools/idea:platform-build-info",  # Used by BuildNumber#currentVersion.
    ],
    iml_files = ["intellij.platform.core.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en[module]",
        "//tools/idea/.idea/libraries:automaton",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)
