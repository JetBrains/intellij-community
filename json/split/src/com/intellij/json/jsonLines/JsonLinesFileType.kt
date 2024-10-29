// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonLines

import com.intellij.icons.AllIcons
import com.intellij.json.JsonBundle
import com.intellij.json.JsonFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@Suppress("CompanionObjectInExtension", "ExtensionClassShouldBeFinalAndNonPublic")
class JsonLinesFileType private constructor() : JsonFileType(JsonLinesLanguage) {
  override fun getName(): @NonNls String = "JSON-lines"

  override fun getDescription(): @NlsContexts.Label String = JsonBundle.message("filetype.json_lines.description")

  override fun getDefaultExtension(): @NlsSafe String = "jsonl"

  override fun getIcon(): Icon = AllIcons.FileTypes.Json

  companion object {
    @JvmField
    val INSTANCE = JsonLinesFileType()
  }
}