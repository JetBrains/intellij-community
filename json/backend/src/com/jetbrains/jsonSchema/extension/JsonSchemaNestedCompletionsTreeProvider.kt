// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode
import com.jetbrains.jsonSchema.impl.nestedCompletions.merge
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point used for extending completion provided by [com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor].
 * Provided instance of [NestedCompletionsNode] will be converted into a completion item with a several level json/yaml tree to insert.
 * See the [NestedCompletionsNode]'s documentation for a more detailed description of how exactly the completion item's text will be constructed.
 */
@ApiStatus.Experimental
interface JsonSchemaNestedCompletionsTreeProvider {
  companion object {
    val EXTENSION_POINT_NAME = ExtensionPointName.create<JsonSchemaNestedCompletionsTreeProvider>(
      "com.intellij.json.jsonSchemaNestedCompletionsTreeProvider")

    @JvmStatic
    fun getNestedCompletionsData(editedFile: PsiFile): NestedCompletionsNode? {
      return EXTENSION_POINT_NAME.extensionsIfPointIsRegistered.asSequence()
        .mapNotNull { extension -> extension.getNestedCompletionsRoot(editedFile) }
        .reduceOrNull { acc, next -> acc.merge(next) }
    }
  }

  /** @return null if you do not want to alter the json schema-based completion for this file */
  fun getNestedCompletionsRoot(editedFile: PsiFile): NestedCompletionsNode?
}