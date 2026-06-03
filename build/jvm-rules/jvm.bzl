load("@rules_kotlin//kotlin/internal:opts.bzl", _kt_javac_options = "kt_javac_options")
load("//:rules/impl/kotlinc-options.bzl", _kt_kotlinc_options = "kt_kotlinc_options")
load("//:rules/impl/transitions.bzl", _jvm_platform_transition = "jvm_platform_transition")
load("//:rules/import.bzl", _jvm_import = "jvm_import")
load("//:rules/library.bzl", _jvm_library = "jvm_library")
load("//:rules/provided-library.bzl", _jvm_provided_library = "jvm_provided_library")
load("//:rules/resource.bzl", _ResourceGroupInfo = "ResourceGroupInfo", _resourcegroup = "resourcegroup")

resourcegroup = _resourcegroup
jvm_library = _jvm_library

jvm_provided_library = _jvm_provided_library
jvm_import = _jvm_import

kt_javac_options = _kt_javac_options
kt_kotlinc_options = _kt_kotlinc_options

# for fleet_plugin_services_resources rule
ResourceGroupInfo = _ResourceGroupInfo
jvm_platform_transition = _jvm_platform_transition
