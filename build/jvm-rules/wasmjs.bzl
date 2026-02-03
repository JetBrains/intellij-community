load("//:rules/import-wasmjs.bzl", _wasmjs_import = "wasmjs_import")
load("//:rules/library-wasmjs.bzl", _wasmjs_library = "wasmjs_library")
load("//:rules/provided-library-wasmjs.bzl", _wasmjs_provided_library = "wasmjs_provided_library")

wasmjs_library = _wasmjs_library
wasmjs_import = _wasmjs_import
wasmjs_provided_library = _wasmjs_provided_library
