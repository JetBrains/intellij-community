// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.util

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.lsp.util.Lsp4jService
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

internal class Lsp4jServiceImpl : Lsp4jService() {
  override fun extractLocationsFromJson(arguments: List<Any>?): List<Location>? {
    if (arguments.isNullOrEmpty()) return null

    val jsonHandler = MessageJsonHandler(emptyMap())
    for (arg in arguments) {
      val jsonElement = jsonHandler.gson.toJsonTree(arg)
      if (jsonElement.isJsonArray && looksLikeLocationArray(jsonElement.asJsonArray)) {
        return jsonHandler.gson.fromJson(jsonElement, Array<Location>::class.java).toList()
      }
    }
    return null
  }

  private fun looksLikeLocationArray(array: com.google.gson.JsonArray): Boolean {
    val first = array.firstOrNull() ?: return false
    if (!first.isJsonObject) return false
    val obj = first.asJsonObject
    return obj.has("uri") && obj.has("range")
  }

  override fun getSnippetText(snippet: String): String {
    val template = LspSnippetTemplate.of(LspSnippetParser.parse(snippet)) { "" }
    val defaults = template.variables.associate { it.name to it.default }
    return template.segments.joinToString("") { segment ->
      when (segment) {
        is LspSnippetTemplate.Segment.Text -> segment.value
        is LspSnippetTemplate.Segment.Stop -> defaults[segment.name].orEmpty()
        LspSnippetTemplate.Segment.End -> ""
      }
    }
  }

  override fun startSnippet(editor: Editor, range: TextRange, snippet: String) {
    val project = editor.project ?: return
    val document = editor.document
    val start = range.startOffset
    document.deleteString(start, range.endOffset)
    val variables = LspSnippetVariables.resolver(FileDocumentManager.getInstance().getFile(document), document, start)
    val template = LspSnippetTemplate.of(LspSnippetParser.parse(StringUtilRt.convertLineSeparators(snippet)), variables)
    editor.selectionModel.removeSelection()
    val plain = template.plainText()
    if (plain != null) {
      document.insertString(start, plain.first)
      editor.caretModel.moveToOffset(start + plain.second)
      return
    }
    editor.caretModel.moveToOffset(start)
    TemplateManager.getInstance(project).runTemplate(editor, template.toTemplate(project))
  }
}
