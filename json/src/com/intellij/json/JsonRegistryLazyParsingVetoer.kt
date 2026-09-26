// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.syntax.JsonLazyParsingVetoer
import com.intellij.openapi.util.registry.Registry

/**
 * Vetoes lazy parsing of JSON when the `json.lazy.parsing` registry flag is turned off.
 */
internal class JsonRegistryLazyParsingVetoer : JsonLazyParsingVetoer {
  override fun isLazyParsingVetoed(): Boolean = !Registry.`is`("json.lazy.parsing", true)
}
