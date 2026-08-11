load("//:rules/binary-wasmjs.bzl", _KtWasmJsBinaryInfo = "KtWasmJsBinaryInfo", _wasmjs_binary = "wasmjs_binary")
load("//:rules/impl/compile-wasmjs.bzl", _KtWasmJsInfo = "KtWasmJsInfo")
load("//:rules/import-wasmjs.bzl", _wasmjs_import = "wasmjs_import")
load("//:rules/library-wasmjs.bzl", _wasmjs_library = "wasmjs_library")
load("//:rules/provided-library-wasmjs.bzl", _wasmjs_provided_library = "wasmjs_provided_library")

wasmjs_library = _wasmjs_library
wasmjs_import = _wasmjs_import
wasmjs_provided_library = _wasmjs_provided_library
wasmjs_binary = _wasmjs_binary
KtWasmJsInfo = _KtWasmJsInfo
KtWasmJsBinaryInfo = _KtWasmJsBinaryInfo
