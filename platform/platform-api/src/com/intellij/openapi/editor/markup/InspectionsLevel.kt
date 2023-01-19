// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.editor.EditorBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Inspection highlight level with string representations bound to resources for i18n.
 */
enum class InspectionsLevel(@PropertyKey(resourceBundle = EditorBundle.BUNDLE) private val nameKey: String,
                            @PropertyKey(resourceBundle = EditorBundle.BUNDLE) private val descriptionKey: String) {
  NONE("iw.level.none.name","iw.level.none.description"),
  SYNTAX("iw.level.syntax.name", "iw.level.syntax.description"),
  ESSENTIAL("iw.level.essential.name", "iw.level.essential.description"),
  ALL("iw.level.all.name", "iw.level.all.description");

  @Nls
  override fun toString(): String = EditorBundle.message(nameKey)

  val description: @Nls String
    get() = EditorBundle.message(descriptionKey)
}