load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.ide",
    srcs = ["src"],
    iml_files = ["intellij.platform.ide.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor",
    ],
    # do not sort: must match IML order
    runtime_deps = ["//tools/idea/.idea/libraries:delight-nashorn-sandbox"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/.idea/libraries:commons-codec",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en[module]",
        "//tools/idea/.idea/libraries:automaton",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/platform/icons:intellij.platform.icons[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/.idea/libraries:proxy-vole",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/.idea/libraries:pty4j",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:ini4j",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:jackson",
        "//tools/idea/.idea/libraries:netty-codec-http",
    ],
)
