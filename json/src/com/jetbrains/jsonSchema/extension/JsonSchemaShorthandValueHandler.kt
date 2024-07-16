// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter

/**
 * Handles a special "shorthand" form of values in some configuration formats.
 * For example, you can have "feature: enabled" and "feature:\n enabled: true".
 * Currently, it's used only in nested completion, but can be reused in other features.
 */
interface JsonSchemaShorthandValueHandler {
  data class KeyValue(val key: String, val value: String)

  companion object {
    @JvmStatic
    val EXTENSION_POINT_NAME: ExtensionPointName<JsonSchemaShorthandValueHandler> = create("com.intellij.json.shorthandValueHandler")
  }

  /**
   * Whether shorthand values can happen in this particular kind of files
   */
  fun isApplicable(file: PsiFile): Boolean

  /**
   * Gets a path within a file (nested property names), and a property literal value
   * Returns a key-value for a replacement
   */
  fun expandShorthandValue(path: List<String>, value: String): KeyValue?

  /**
   * Gets a path within a file (nested property names), and a property name and value in the full form
   * Returns a collapsed representation of that property
   */
  fun collapseToShorthandValue(path: List<String>, data: KeyValue): String? = null

  /**
   * Whether the object is collapsible. By default, it's collapsible if there is just one property.
   */
  fun isCollapsible(parentObject: JsonObjectValueAdapter): Boolean = parentObject.propertyList.size == 1
}
