"""Macros for IntelliJ-based IDE development builds."""

load("@rules_java//java:defs.bzl", "java_binary")
load(":intellij_dev.bzl", "intellij_dev_binary")
load("@jps_dynamic_deps_community//:targets.bzl", "ALL_PRODUCTION_COMMUNITY_TARGETS", "ALL_LIBRARY_COMMUNITY_TARGETS")

intellij_dev_binary_community = macro(
    doc = """Macro for IDEA-based dev-build targets.

    Creates a dev launcher for IntelliJ IDEA-based products using
    the DevMainKt entry point from the dev server.
    """,
    implementation = intellij_dev_binary,
    attrs = {
        "data": attr.label_list(default = ALL_PRODUCTION_COMMUNITY_TARGETS + ALL_LIBRARY_COMMUNITY_TARGETS, doc = "Data dependencies. Defaults to ALL_PRODUCTION_COMMUNITY_TARGETS + ALL_LIBRARY_COMMUNITY_TARGETS."),
        "jvm_flags": attr.string_list(default = [], configurable = False, doc = "Additional JVM flags."),
        "env": attr.string_dict(default = {}, configurable = False, doc = "Environment variables to set when running the binary."),
        "platform_prefix": attr.string(configurable = False, doc = "Value for -Didea.platform.prefix (e.g., 'idea', 'GoLand')."),
        "config_path": attr.string(configurable = False, doc = "Path for -Didea.config.path. Defaults to out/dev-data/{name}/config."),
        "system_path": attr.string(configurable = False, doc = "Path for -Didea.system.path. Defaults to out/dev-data/{name}/system."),
        "additional_modules": attr.string(configurable = False, doc = "Value for -Dadditional.modules flag (optional)."),
        "program_args": attr.string_list(default = [], configurable = False, doc = "Value for program arguments (optional)."),
    },
)
