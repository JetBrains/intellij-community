// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.lang.documentation.QuickDocHighlightingHelper.getStyledFragment
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContext.ParameterHtmlPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.applyIf
import com.intellij.util.ui.UIUtil
import java.awt.Color

internal fun disabledSignatureAlpha(isDarkTheme: Boolean) =
  if (isDarkTheme) 0.5 else 0.75

internal fun selectedSignatureAlpha(isDarkTheme: Boolean) =
  if (isDarkTheme) 0.05 else 0.15

private val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode

@NlsSafe
internal fun renderSignaturePresentationToHtml(
  editor: Editor,
  context: ParameterInfoUIContext,
  parametersPresentation: List<ParameterHtmlPresentation>,
  currentParameterIndex: Int,
  separator: String,
  isDeprecated: Boolean,
): String {
  val backgroundColor = context.defaultParameterColor ?: JBColor.WHITE
  val isDarkTheme = ColorUtil.isDark(backgroundColor)
  val deselectedParamAlpha = if (isDarkTheme) 0.6 else 0.9
  val defaultParamAlpha = if (isDarkTheme) 0.5 else 0.75
  val disabledSignatureAlpha = disabledSignatureAlpha(isDarkTheme)
  val mismatchedParameterAlpha = if (isDarkTheme) 0.05 else 0.2
  val textAttributes = TextAttributes().apply {
    foregroundColor = EditorColorsManager.getInstance().getGlobalScheme().let {
      it.getAttributes(HighlighterColors.TEXT).foregroundColor
      ?: it.defaultForeground
    }
  }
  val currentParameter = currentParameterIndex

  val mismatchedParameterBgColor = "#${ColorUtil.toHex(ColorUtil.blendColorsInRgb(backgroundColor, JBColor.RED, mismatchedParameterAlpha))}"
  val backgroundColorHex = "#${ColorUtil.toHex(backgroundColor)}"
  val separatorStr = "<span style=\"color:#${ColorUtil.toHex(textAttributes.foregroundColor)};\">$separator</span>"
  val parameters = parametersPresentation
    .mapIndexed { index, parameter ->
      val defaultParam =
        parameter.defaultValue
          ?.let { blendColors(getStyledFragment(it, textAttributes), backgroundColor, defaultParamAlpha) }
        ?: ""
      val addSeparator = if (index < parametersPresentation.size - 1) separatorStr else ""
      val result = if (index == currentParameter)
        "<b>${getStyledFragment(parameter.nameAndType, textAttributes) + defaultParam + addSeparator}</b>"
      else
        blendColors(getStyledFragment(parameter.nameAndType, textAttributes) + defaultParam + addSeparator, backgroundColor, deselectedParamAlpha)
      result
        .replace(Regex("</?a([^a-zA-Z>][^>]*>|>)"), "")
        .let {
          if (parameter.isMismatched) {
            if (isUnitTestMode)
              "<mismatched>$it</mismatched>"
            else
              "<code style='background-color: $mismatchedParameterBgColor; border-color: $mismatchedParameterBgColor'>$it</code>"
          }
          else if (!isUnitTestMode) {
            "<code>$it</code>"
          } else it
        }
    }

  return """<style>
    |code {
    |  background-color: $backgroundColorHex; 
    |  border-color: $backgroundColorHex; 
    |  padding: ${JBUIScale.scale(3)}px ${JBUIScale.scale(3)}px;
    |}
    |</style>
    |""".trimMargin() +
         buildContents(parameters, parametersPresentation, isDeprecated, currentParameter, editor, backgroundColor,
                       disabledSignatureAlpha, context.isSingleOverload)
}

@NlsSafe
private fun buildContents(
  parameters: List<String>,
  parametersPresentation: List<ParameterHtmlPresentation>,
  isDeprecated: Boolean,
  currentParameter: Int,
  editor: Editor,
  backgroundColor: Color,
  disabledSignatureAlpha: Double,
  singleOverload: Boolean,
): String {
  val result = StringBuilder()
  val lineBuffer = StringBuilder()
  var lineWidth = 0

  val maxLineWidth = ParameterInfoComponent.getWidthLimit(editor)
  val boldFont = ParameterInfoComponent.getBoldFont(editor)

  var index = 0
  val fontMetrics = editor.component.getFontMetrics(boldFont)

  val leadingParameterIsMismatched = parametersPresentation
    .applyIf(currentParameter >= 0) { take(currentParameter) }
    .any { it.isMismatched }

  fun StringBuilder.appendCurrentLine(): StringBuilder {
    append(lineBuffer.toString()
             .applyIf(isDeprecated) { "<strike>$this</strike>" }
             .applyIf(leadingParameterIsMismatched) { blendColors(this, backgroundColor, disabledSignatureAlpha) })
    lineBuffer.clear()
    lineWidth = 0
    return this
  }

  // If there are multiple overloads, add indentation for multiline signatures for clarity
  val lineSeparator = "<br>" + if (!singleOverload) "&nbsp;&nbsp;&nbsp;&nbsp;" else ""

  while (index < parameters.size) {
    val parameterText = parameters[index]
      .applyIf(index < parameters.size - 1) { if (isUnitTestMode) "$this " else "$this&ThinSpace;" }
      .replace("<br>", lineSeparator)
    val textNoHtml = StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(parameterText))
    val firstLine = textNoHtml.takeWhile { it != '\n' }
    val firstLineTextWidth: Int = fontMetrics.stringWidth(firstLine)
    if (lineBuffer.isNotEmpty() && lineWidth + firstLineTextWidth > maxLineWidth) {
      result
        .appendCurrentLine()
        .append(lineSeparator)
    }
    lineBuffer.append(parameterText)
    if (firstLine.length == textNoHtml.length)
      lineWidth += firstLineTextWidth
    else
      lineWidth += fontMetrics.stringWidth(textNoHtml.takeLastWhile { it != '\n' })
    index++
  }

  result.appendCurrentLine()
  return result.toString()
}

private fun blendColors(text: String, background: Color, alpha: Double): String =
  if (isUnitTestMode)
    text
  else
    text.replace(Regex("color: *#([0-9a-f]+);")) {
      val colorText = it.groupValues.getOrNull(1) ?: return@replace it.value
      val color = ColorHexUtil.fromHex(colorText)
      "color:#${ColorUtil.toHex(ColorUtil.blendColorsInRgb(background, color, alpha))};"
    }