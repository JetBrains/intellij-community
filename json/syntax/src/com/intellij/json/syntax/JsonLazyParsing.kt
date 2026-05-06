// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import fleet.util.multiplatform.linkToActual

internal fun jsonLazyParsing(): Boolean = linkToActual()

/**
 * Handles lazy parsing switch of JSON files in syntax lib which has no dependency on IntelliJ platform.
 *
 * @see jsonLazyParsingJvm, jsonLazyParsingWasmJs
 */
val JsonLazyParsing: Boolean = jsonLazyParsing()