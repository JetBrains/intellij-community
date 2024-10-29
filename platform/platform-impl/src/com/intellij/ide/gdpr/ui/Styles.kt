// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr.ui
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

@ApiStatus.Internal
object Styles {
    private val foregroundColor: Color = JBColor.BLACK
    private val hintColor: Color = JBColor.GRAY
    private val headerColor: Color = JBColor.BLACK
    private val linkColor = Color(74, 120, 194)
    private val lineSpacing = 0.1f
    private val fontSize = StartupUiUtil.labelFont.size
    private val h1FontSize = JBUIScale.scale(24)
    private val h2FontSize = JBUIScale.scale(18)

    val H1: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, headerColor)
        StyleConstants.setFontSize(this, h1FontSize)
        StyleConstants.setBold(this, true)
        StyleConstants.setSpaceBelow(this, h1FontSize * 0.6f)
    }
    val H2: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, headerColor)
        StyleConstants.setFontSize(this, h2FontSize)
        StyleConstants.setBold(this, true)
        StyleConstants.setSpaceAbove(this, h2FontSize * 0.8f)
    }
    val REGULAR: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, foregroundColor)
        StyleConstants.setFontSize(this, fontSize)
        StyleConstants.setBold(this, false)
    }
    val BOLD: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, foregroundColor)
        StyleConstants.setBold(this, true)
    }
    val SUP: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, foregroundColor)
        StyleConstants.setSuperscript(this, true)
    }
    val LINK: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, linkColor)
    }

    val PARAGRAPH: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, foregroundColor)
        StyleConstants.setLineSpacing(this, lineSpacing)
        StyleConstants.setFontSize(this, fontSize)
        StyleConstants.setSpaceAbove(this, fontSize * 0.6f)
    }

    val HINT: SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setForeground(this, hintColor)
        StyleConstants.setLineSpacing(this, lineSpacing)
        StyleConstants.setFontSize(this, fontSize)
        StyleConstants.setSpaceAbove(this, fontSize * 0.6f)
    }
}