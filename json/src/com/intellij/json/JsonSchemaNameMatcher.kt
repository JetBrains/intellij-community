// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Tells if the JSON schema of the file defines a string literal, for example as a property name or an enum value.
 * The spellchecker and Grazie do not check such a literal.
 *
 * The JSON schema implementation is in `intellij.json.backend`.
 * Without the backend, no literal matches.
 */
@ApiStatus.Internal
interface JsonSchemaNameMatcher {
  fun matches(literal: JsonStringLiteral): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<JsonSchemaNameMatcher>("com.intellij.json.schemaNameMatcher")

    @JvmStatic
    fun matchesNameFromSchema(literal: JsonStringLiteral): Boolean {
      return EP_NAME.extensionList.any { it.matches(literal) }
    }
  }
}
