// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.lang.Language
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.EntityConverter
import java.awt.Color
import kotlin.math.abs

internal class DocSanitizingTagGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val nodeText = node.getTextInNode(text)
    val matcher = DocMarkdownToHtmlConverter.TAG_PATTERN.matcher(nodeText)
    if (matcher.matches()) {
      val tagName = matcher.group(1)
      if (StringUtil.equalsIgnoreCase(tagName, "div")) {
        visitor.consumeHtml(nodeText.subSequence(0, matcher.start(1)))
        visitor.consumeHtml("span")
        visitor.consumeHtml(nodeText.subSequence(matcher.end(1), nodeText.length))
        return
      }
      if (DocMarkdownToHtmlConverter.ACCEPTABLE_TAGS.contains(tagName)) {
        visitor.consumeHtml(nodeText)
        return
      }
    }
    visitor.consumeHtml(StringUtil.escapeXmlEntities(nodeText.toString()))
  }
}

internal class DocCodeFenceGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length

    var state = 0

    var childrenToConsider = node.children
    if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
      childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size - 1)
    }

    val contents = StringBuilder()
    var language: String? = null
    for (child in childrenToConsider) {
      if (state == 1 && child.type in listOf(MarkdownTokenTypes.CODE_FENCE_CONTENT,
                                             MarkdownTokenTypes.EOL)) {
        contents.append(HtmlGenerator.trimIndents(child.getTextInNode(text), indentBefore))
      }
      if (state == 0 && child.type == MarkdownTokenTypes.FENCE_LANG) {
        language = HtmlGenerator.leafText(text, child).toString().trim().split(' ')[0]
      }
      if (state == 0 && child.type == MarkdownTokenTypes.EOL) {
        state = 1
      }
    }
    val trimmedContents = contents.toString().trim('\n', '\r')
    val coloredCode =
      language
        ?.toIdeLanguage()
        ?.let {
          HtmlSyntaxInfoUtil.getHtmlContent(
            PsiFileFactory.getInstance(DefaultProjectFactory.getInstance().defaultProject)
              .createFileFromText(it, trimmedContents),
            trimmedContents,
            null,
            EditorColorsManager.getInstance().globalScheme,
            0,
            trimmedContents.length
          )
        }
        ?.toString()
      ?: EntityConverter.replaceEntities(contents, false, false)
    visitor.consumeHtml(wrapWithCodeBackground(coloredCode, true))
  }

  private fun String.toIdeLanguage(): Language? =
    Language.findInstancesByMimeType(this)
      .asSequence()
      .plus(Language.findInstancesByMimeType("text/$this"))
      .plus(
        Language.getRegisteredLanguages()
          .asSequence()
          .filter { languageMatches(this, it) }
      )
      .firstOrNull()

  private fun languageMatches(langType: String, language: Language): Boolean =
    langType.equals(language.id, ignoreCase = true)
    || FileTypeManager.getInstance().getFileTypeByExtension(langType) === language.associatedFileType

}

internal class DocCodeSpanGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val nodes = node.children.subList(1, node.children.size - 1)
    val output = nodes.joinToString(separator = "") { HtmlGenerator.leafText(text, it, false) }.trim()
    visitor.consumeHtml(wrapWithCodeBackground(output, false))
  }
}

private fun wrapWithCodeBackground(codeInnerHtml: String, isBlock: Boolean): String {
  val globalScheme = EditorColorsManager.getInstance().globalScheme
  val codeBg = getCodeFragmentBg()
  val codeBgHex = "#${UIUtil.colorToHex(codeBg)}"
  val codeFgHex = "#${UIUtil.colorToHex(globalScheme.defaultForeground)}"
  val fontFamily = globalScheme.editorFontName
  val fontSizePercent = Math.round(100.0 * (FontPreferences.DEFAULT_FONT_SIZE - 1) / FontPreferences.DEFAULT_FONT_SIZE).toInt()
  return if (isBlock)
    "<div style=\"background-color: $codeBgHex; color: $codeFgHex; margin: 5px 0px 5px 10px; padding: 6px; font-family: $fontFamily; font-size: $fontSizePercent%;\">" +
    "<pre style=\"padding: 0px; margin: 0px\">${codeInnerHtml}</pre>" +
    "</div>"
  else
    "<span style=\"background-color: $codeBgHex; color: $codeFgHex; font-family: $fontFamily; font-size: $fontSizePercent%; padding: 1px 4px; margin: 1px 0px;\">$codeInnerHtml</span>"
}

private fun getCodeFragmentBg(): Color {
  // Documentation renders with ToolTipActionBackground color,
  // so it's best to use the same color
  val actionBg = JBUI.CurrentTheme.Editor.Tooltip.BACKGROUND
  val tooltipBg = UIUtil.getToolTipActionBackground()
  val tooltipLuminance = ColorUtil.getLuminance(tooltipBg)
  val actionLuminance = ColorUtil.getLuminance(actionBg)

  val diff = tooltipLuminance - actionLuminance
  if (tooltipLuminance > 0.5) {
    if (abs(diff) < 0.1) {
      if (diff < 0) {
        return ColorUtil.hackBrightness(actionBg, 1, 0.95f)
      }
      else {
        return ColorUtil.hackBrightness(actionBg, 1, 1.02f)
      }
    }
  }
  return actionBg
}