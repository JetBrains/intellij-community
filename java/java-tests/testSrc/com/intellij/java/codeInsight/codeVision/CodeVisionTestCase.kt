// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import java.util.regex.Pattern

abstract class CodeVisionTestCase : InlayHintsProviderTestCase() {
  override fun setUp() {
    Registry.get("editor.codeVision.new").setValue(true, testRootDisposable)
    super.setUp()
  }

  protected fun testProviders(expectedText: String, fileName: String, vararg enabledProviderIds: String) {
    // set enabled providers
    val settings = CodeVisionSettings.instance()
    val codeVisionHost = CodeVisionHost.getInstance(project)
    codeVisionHost.providers.forEach {
      settings.setProviderEnabled(it.id, enabledProviderIds.contains(it.id))
    }

    val sourceText = CodeVisionInlayData.pattern.matcher(expectedText).replaceAll("")
    myFixture.configureByText(fileName, sourceText)

    val editor = myFixture.editor
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    myFixture.doHighlighting()

    codeVisionHost.calculateCodeVisionSync(editor, testRootDisposable)

    val actualText = dumpCodeVisionHints(sourceText)
    assertEquals(expectedText, actualText)
  }

  private fun dumpCodeVisionHints(sourceText: String): String {
    val file = myFixture.file!!
    val editor = myFixture.editor
    val model = editor.inlayModel
    val range = file.textRange
    val inlineElements = model.getInlineElementsInRange(range.startOffset, range.endOffset)
    val afterLineElements = model.getAfterLineEndElementsInRange(range.startOffset, range.endOffset)
    val blockElements = model.getBlockElementsInRange(range.startOffset, range.endOffset)
    val inlays = mutableListOf<CodeVisionInlayData>()
    inlineElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Block) }
    val document = myFixture.getDocument(file)
    inlays.sortBy { it.effectiveOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        val nextOffset = inlay.effectiveOffset(document)
        append(sourceText.subSequence(currentOffset, nextOffset))
        append(inlay)
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  private data class CodeVisionInlayData(val inlay: Inlay<*>, val type: InlayType) {
    fun effectiveOffset(document: Document): Int {
      return when (type) {
        InlayType.Inline -> inlay.offset
        InlayType.Block -> {
          val offset = inlay.offset
          val lineNumber = document.getLineNumber(offset)
          document.getLineStartOffset(lineNumber)
        }
      }
    }

    override fun toString(): String {
      val renderer = inlay.renderer
      if (renderer !is CodeVisionRenderer) error("renderer not supported")
      return buildString {
        append("<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(inlay.getUserData(CodeVisionListData.KEY)?.visibleLens?.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation })
        append(" #>")
        if (type == InlayType.Block) {
          append('\n')
        }
      }
    }

    companion object {
      val pattern: Pattern = Pattern.compile("<# block ([^#]*)#>(\r\n|\r|\n)|<#([^#]*)#>")
    }
  }
}