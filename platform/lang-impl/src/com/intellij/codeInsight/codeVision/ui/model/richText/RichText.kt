// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model.richText

import com.intellij.openapi.util.TextRange
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Font

// please consider talking to someone from Rider Team before changing anything here

data class RichString(val textRange: TextRange, var attributes: SimpleTextAttributes, val richText: RichText) {
  val text: String get() = richText.text.substring(textRange.startOffset, textRange.endOffset)
}

open class RichText(text: String, parts: Collection<RichString>) : Cloneable {
  private var myString: String = text
  private var myParts = ArrayList<RichString>(parts)
  val parts: List<RichString> get() = myParts
  val text: String get() = myString
  val length: Int get() = text.length

  constructor() : this("", emptyList())
  constructor(text: String) : this() {
    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  fun append(text: String, style: SimpleTextAttributes) {
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
    val acc = StringBuilder()
    for (p in myParts) {
      val (r, a) = p
      acc.apply {
        append("<span styles=\"")
        val bgColor = a.bgColor
        if (bgColor != null) {
          append("background-color:")
          dumpColor(bgColor)
        }
        val fgColor = a.fgColor
        if (fgColor != null) {
          append("color:")
          dumpColor(fgColor)
        }
        val waveColor = a.waveColor
        if (waveColor != null) {
          append("wave-color:")
          dumpColor(fgColor)
        }
        dumpStyleAndFont(a)
        append("")
        append("\">")

        append(p.text)
        append("</span>")
      }
    }
    return acc.toString()
  }

  private fun StringBuilder.dumpStyleAndFont(a: SimpleTextAttributes) {
    when (a.fontStyle) {
      Font.PLAIN -> append("font-style: plain;")
      Font.ITALIC -> append("font-style: italic;")
      Font.BOLD -> append("font-weight: bold;")
    }
    when {
      a.isSearchMatch -> append("text-decoration: searchMatch;")
      a.isStrikeout -> append("text-decoration: strikeout;")
      a.isWaved -> append("text-decoration: waved;")
      a.isUnderline -> append("text-decoration: underline;")
      a.isBoldDottedLine -> append("text-decoration: boldDottedLine;")
      a.isOpaque -> append("text-decoration: opaque;")
      a.isSmaller -> append("text-decoration: smaller;")
    }
  }

  private fun StringBuilder.dumpColor(bgColor: Color) {
    append("rgb(")
    append(bgColor.red)
    append(",")
    append(bgColor.green)
    append(",")
    append(bgColor.blue)
    append(");")
  }
}