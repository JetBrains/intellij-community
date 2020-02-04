load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.externalSystem",
    srcs = ["src"],
    iml_files = ["intellij.platform.externalSystem.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer[module]",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer.annotations[module]",
        "//tools/idea/.idea/libraries:lz4-java",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
