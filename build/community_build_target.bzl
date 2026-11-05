"""Macros for IntelliJ community build targets."""

load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("@jps_dynamic_deps_community//:targets.bzl", "ALL_COMMUNITY_TARGETS", "BAZEL_TARGETS_JSON_COMMUNITY")
load("@rules_java//java:defs.bzl", "java_binary")
load("@rules_java//java/bazel/rules:bazel_java_binary.bzl", java_binary_rule = "java_binary")

def _or_empty(value):
    return [] if value == None else value

def _community_build_target_impl(name, **kwargs):
    args = {key: value for key, value in kwargs.items() if value != None}
    args["add_opens"] = INTELLIJ_ADD_OPENS + _or_empty(kwargs.get("add_opens"))
    args["data"] = ALL_COMMUNITY_TARGETS + [BAZEL_TARGETS_JSON_COMMUNITY] + _or_empty(kwargs.get("data"))
    args["jvm_flags"] = [
        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % BAZEL_TARGETS_JSON_COMMUNITY,
    ] + _or_empty(kwargs.get("jvm_flags"))
    java_binary(name = name, **args)

# Keep in sync with `ultimate_build_target` in `build/ultimate_build_target.bzl` of the ultimate repository.
# The two macros differ only in the targets data.
community_build_target = macro(
    doc = "A Java binary with the shared community build target defaults.",
    implementation = _community_build_target_impl,
    inherit_attrs = java_binary_rule,
    attrs = {
        "add_opens": attr.string_list(doc = "Additional Java packages to open."),
        "data": attr.label_list(doc = "Additional runtime data."),
        "jvm_flags": attr.string_list(doc = "Additional JVM flags."),
    },
)
