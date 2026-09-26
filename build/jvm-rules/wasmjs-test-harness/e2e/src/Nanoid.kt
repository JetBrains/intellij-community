// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JsModule("nanoid")

package e2e.nanoid

// Minimal binding to nanoid, imported as a bare specifier: exercises the import map
// generated from wasmjs_test's npm_packages attribute (and the `exports`-map branch of
// npm entry resolution).
external fun nanoid(): String
