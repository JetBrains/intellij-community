// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight

import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonElementGenerator
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.StringPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import kotlin.math.roundToInt

@ApiStatus.Internal
class JsonColorProvider : ElementColorProvider {
  override fun getColorFrom(element: PsiElement): Color? {
    val literal = getLiteral(element) ?: return null
    val value = literal.value.trim()
    if (value.length > 256) return null
    val chars = StringPattern.newBombedCharSequence(value)
    if (HEX_PATTERN.matches(chars)) return ColorHexUtil.fromHexOrNull(value)

    val body = RGB_FUNCTION.matchEntire(chars)?.groupValues?.get(1) ?: return null
    val legacy = ',' in body
    val match = (if (legacy) LEGACY_RGB else MODERN_RGB).matchEntire(StringUtil.newBombedCharSequence(body, 300)) ?: return null
    val components = match.groupValues.drop(1)
    if (legacy && components.take(3).map { it.endsWith('%') }.distinct().size != 1) return null
    val red = component(components[0], 255.0) ?: return null
    val green = component(components[1], 255.0) ?: return null
    val blue = component(components[2], 255.0) ?: return null
    val alpha = if (components[3].isEmpty()) 255 else component(components[3], 1.0) ?: return null
    return Color(red, green, blue, alpha)
  }

  override fun setColorTo(element: PsiElement, color: Color) {
    val literal = element.getUserData(COLOR_LITERAL)?.element ?: getLiteral(element) ?: return
    if (!literal.isWritable) return
    if (element.getUserData(COLOR_LITERAL) == null) {
      element.putUserData(COLOR_LITERAL, SmartPointerManager.createPointer(literal))
    }

    val value = literal.value.trim()
    val text = if (value.startsWith('#')) {
      "#" + ColorUtil.toHex(color, color.alpha < 255 || value.length == 5 || value.length == 9)
    }
    else {
      val legacy = ',' in value
      val separator = if (legacy) ", " else " "
      val alphaSeparator = if (legacy) ", " else " / "
      val alpha = if (color.alpha < 255) "$alphaSeparator${color.alpha / 255.0}" else ""
      val function = if (legacy && color.alpha < 255) "rgba" else "rgb"
      "$function(${color.red}$separator${color.green}$separator${color.blue}$alpha)"
    }
    WriteCommandAction.writeCommandAction(literal.project, literal.containingFile)
      .withName(JsonBundle.message("command.name.update.json.color"))
      .run<RuntimeException> {
        (literal as PsiLanguageInjectionHost).updateText(JsonElementGenerator(literal.project).createStringLiteral(text).text)
      }
  }

  private fun getLiteral(element: PsiElement): JsonStringLiteral? {
    if (!element.isValid || element.firstChild != null) return null
    val literal = element.parent as? JsonStringLiteral ?: return null
    if (literal.isPropertyName || literal.containingFile.name.endsWith(".theme.json", ignoreCase = true)) return null
    return literal
  }

  private fun component(text: String, maximum: Double): Int? {
    val percentage = text.endsWith('%')
    val number = (if (percentage) text.dropLast(1) else text).toDoubleOrNull() ?: return null
    if (!number.isFinite()) return null
    val scale = if (percentage) 100.0 else maximum
    return (number.coerceIn(0.0, scale) / scale * 255).roundToInt()
  }

  companion object {
    private val COLOR_LITERAL = Key.create<SmartPsiElementPointer<JsonStringLiteral>>("json.color.literal")
    private val HEX_PATTERN = Regex("""#(?:[a-f0-9]{3,4}|[a-f0-9]{6}|[a-f0-9]{8})""", RegexOption.IGNORE_CASE)
    private val RGB_FUNCTION = Regex("""rgba?\(([\s\S]*)\)""", RegexOption.IGNORE_CASE)
    private const val COMPONENT = """([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?%?)"""
    private val LEGACY_RGB = Regex("""\s*$COMPONENT\s*,\s*$COMPONENT\s*,\s*$COMPONENT(?:\s*,\s*$COMPONENT)?\s*""")
    private val MODERN_RGB = Regex("""\s*$COMPONENT\s+$COMPONENT\s+$COMPONENT(?:\s*/\s*$COMPONENT)?\s*""")
  }
}
