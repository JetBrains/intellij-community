load("//:rules/library.bzl", _jvm_library = "jvm_library")
load("//:rules/provided-library.bzl", _jvm_provided_library = "jvm_provided_library")
load("//:rules/import.bzl", _jvm_import = "jvm_import")
load("//:rules/resource.bzl", _jvm_resources = "jvm_resources")
load("//:rules/test.bzl", _jvm_test = "jvm_test")
load("//:rules/impl/javac-options.bzl", _kt_javac_options = "kt_javac_options")
load("//:rules/impl/kotlinc-options.bzl", _kt_kotlinc_options = "kt_kotlinc_options")

jvm_resources = _jvm_resources
jvm_library = _jvm_library
jvm_test = _jvm_test

jvm_provided_library = _jvm_provided_library
jvm_import = _jvm_import

kt_javac_options = _kt_javac_options
kt_kotlinc_options = _kt_kotlinc_options
