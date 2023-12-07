// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model.richText

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Font

// please consider talking to someone from Rider Team before changing anything here

data class RichString(val textRange: TextRange, var attributes: SimpleTextAttributes, val richText: RichText) {
  val text: String get() = richText.text.substring(textRange.startOffset, textRange.endOffset)
}

open class RichText(@Nls text: String, parts: Collection<RichString>) : Cloneable {
  @Nls
  private var myString: String = text
  private var myParts = ArrayList<RichString>(parts)
  val parts: List<RichString> get() = myParts
  val text: String @Nls get() = myString
  val length: Int get() = text.length

  constructor() : this("", emptyList())
  constructor(@Nls text: String) : this() {
    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  fun append(@Nls text: String, style: SimpleTextAttributes) {
    val range = TextRange(myString.length, myString.length + text.length)
    myString += text
    myParts.add(RichString(range, style, this))
  }

  fun setForeColor(fgColor: Color) {
    for (part in parts) {
      val attributes = part.attributes
      val style = attributes.style
      val bgColor = attributes.bgColor
      val waveColor = attributes.waveColor
      part.attributes = attributes.derive(style, fgColor, bgColor, waveColor)
    }
  }

  fun addStyle(@SimpleTextAttributes.StyleAttributeConstant style: Int, textRange: TextRange) {
    if (textRange.startOffset !in 0 until length) throw IllegalArgumentException("textRange")
    if (textRange.endOffset !in 0..length)
      throw IllegalArgumentException("textRange")

    val newParts = ArrayList<RichString>()

    for (part in myParts) {
      val (partRange, attributes) = part
      val intersection: TextRange? = textRange.intersection(partRange)
      if (intersection == null) {
        newParts.add(part)
        continue
      } else {
        if (partRange.startOffset != intersection.startOffset) {
          newParts.add(RichString(TextRange(partRange.startOffset, intersection.startOffset), attributes, this))
        }
        val newAttributes = attributes.derive(attributes.style or style, null, null, null)
        newParts.add(RichString(intersection, newAttributes, this))
        if (intersection.endOffset != partRange.endOffset) {
          newParts.add(RichString(TextRange(intersection.endOffset, partRange.endOffset), attributes, this))
        }
      }
    }
    myParts = newParts
  }


  public override fun clone(): RichText {
    val copy = RichText()
    copy.myString = myString
    for ((textRange, attributes) in myParts) copy.myParts.add(RichString(textRange, attributes, copy))
    return copy
  }

  @Nls
  override fun toString(): String {
    val htmlBuilder = HtmlBuilder()
    for(p in myParts){
      val (_, a) = p
      val style = calculateStyle(a)
      val text = p.text.trimEnd()
      var span = HtmlChunk.span(style).addText(p.text)

      // Add trim whitespaces to preserve them in HTML
      if (p.text.length > text.length) {
        span = span.addRaw("&nbsp;".repeat(p.text.length - text.length))
      }
      htmlBuilder.append(span)
    }

    return htmlBuilder.toString()
  }

  private fun calculateStyle(textAttributes: SimpleTextAttributes): String {
    val styleBuilder = StringBuilder()
    val bgColor = textAttributes.bgColor
    if (bgColor != null) {
      styleBuilder.append("background-color:${bgColor.dumpColor()}")
    }
    val fgColor = textAttributes.fgColor
    if (fgColor != null) {
      styleBuilder.append("color:${fgColor.dumpColor()}")
    }
    val waveColor = textAttributes.waveColor
    if (waveColor != null) {
      styleBuilder.append("wave-color:${waveColor.dumpColor()}")
    }
    styleBuilder.append(textAttributes.dumpStyleAndFont())
    return styleBuilder.toString()
  }


  private fun SimpleTextAttributes.dumpStyleAndFont() : String {
    val font = when (fontStyle) {
      Font.PLAIN -> "font-style: plain;"
      Font.ITALIC -> "font-style: italic;"
      Font.BOLD -> "font-weight: bold;"
      else -> ""
    }

    val decoration = when {
      isSearchMatch -> "text-decoration: searchMatch;"
      isStrikeout -> "text-decoration: strikeout;"
      isWaved -> "text-decoration: waved;"
      isUnderline -> "text-decoration: underline;"
      isBoldDottedLine -> "text-decoration: boldDottedLine;"
      isOpaque -> "text-decoration: opaque;"
      isSmaller -> "text-decoration: smaller;"
      else -> ""
    }

    return "$font$decoration"
  }

  private fun Color.dumpColor() :String{
    return "rgb(${red},${green},${blue});"
  }
}