load("//:rules/import.bzl", _wasmjs_import = "wasmjs_import")
load("//:rules/library.bzl", _kt_wasmjs_library = "kt_wasmjs_library")
load("//:rules/provided-library.bzl", _wasmjs_provided_library = "wasmjs_provided_library")

kt_wasmjs_library = _kt_wasmjs_library
wasmjs_import = _wasmjs_import
wasmjs_provided_library = _wasmjs_provided_library
