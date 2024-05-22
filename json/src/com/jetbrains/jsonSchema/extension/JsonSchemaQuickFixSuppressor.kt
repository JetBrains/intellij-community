// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.PsiFile

/**
 * Implement to suppress a JSON schema-related quick fix or intention action for your file
 * Currently supported actions to suppress are:
 *  - AddOptionalPropertiesIntention
 */
interface JsonSchemaQuickFixSuppressor {
  companion object {
    @JvmStatic
    val EXTENSION_POINT_NAME: ExtensionPointName<JsonSchemaQuickFixSuppressor> = create("com.intellij.json.jsonSchemaQuickFixSuppressor")
  }

  fun shouldSuppressFix(file: PsiFile, quickFixClass: Class<out IntentionAction>): Boolean
}