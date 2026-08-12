"""Macros for IntelliJ-based IDE development builds."""

load("@jps_dynamic_deps_community//:targets.bzl", "ALL_LIBRARY_COMMUNITY_TARGETS", "ALL_PRODUCTION_COMMUNITY_TARGETS", "BAZEL_TARGETS_JSON_COMMUNITY")
load("@rules_java//java:defs.bzl", "java_binary")
load(":dev_launch_dependencies.bzl", "COMMUNITY_DEV_LAUNCH_REPOS")
load(":intellij_dev.bzl", "intellij_dev_binary")

def _intellij_dev_binary_community_impl(
        name,
        visibility,
        data,
        jvm_flags,
        env,
        platform_prefix,
        bazel_targets_json,
        config_path,
        system_path,
        additional_modules,
        program_args):
    intellij_dev_binary(
        name = name,
        visibility = visibility,
        data = data,
        jvm_flags = jvm_flags,
        env = env,
        platform_prefix = platform_prefix,
        bazel_targets_json = bazel_targets_json,
        config_path = config_path,
        system_path = system_path,
        additional_modules = additional_modules,
        program_args = program_args,
        preloaded_download_repos = COMMUNITY_DEV_LAUNCH_REPOS,
    )

intellij_dev_binary_community = macro(
    doc = """Macro for IDEA-based dev-build targets.

    Creates a dev launcher for IntelliJ IDEA-based products using
    the DevMainKt entry point from the dev server.
    """,
    implementation = _intellij_dev_binary_community_impl,
    attrs = {
        "data": attr.label_list(default = ALL_PRODUCTION_COMMUNITY_TARGETS + ALL_LIBRARY_COMMUNITY_TARGETS, doc = "Data dependencies. Defaults to ALL_PRODUCTION_COMMUNITY_TARGETS + ALL_LIBRARY_COMMUNITY_TARGETS."),
        "jvm_flags": attr.string_list(default = [], configurable = False, doc = "Additional JVM flags."),
        "env": attr.string_dict(default = {}, configurable = False, doc = "Environment variables to set when running the binary."),
        "platform_prefix": attr.string(configurable = False, doc = "Value for -Didea.platform.prefix (e.g., 'idea', 'GoLand')."),
        "bazel_targets_json": attr.label(default = BAZEL_TARGETS_JSON_COMMUNITY, allow_single_file = True, configurable = False, doc = "bazel-targets.json generated from JPS project model (community)"),
        "config_path": attr.string(configurable = False, doc = "Path for -Didea.config.path. Defaults to out/dev-data/{name}/config."),
        "system_path": attr.string(configurable = False, doc = "Path for -Didea.system.path. Defaults to out/dev-data/{name}/system."),
        "additional_modules": attr.string(configurable = False, doc = "Value for -Dadditional.modules flag (optional)."),
        "program_args": attr.string_list(default = [], configurable = False, doc = "Value for program arguments (optional)."),
    },
)
