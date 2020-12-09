// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor.EvaluationResult
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.lang.Long.parseLong
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import kotlin.math.*

class CalculatorSEContributor : WeightedSearchEverywhereContributor<EvaluationResult> {

  class Factory : SearchEverywhereContributorFactory<EvaluationResult> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<EvaluationResult> {
      return CalculatorSEContributor()
    }
  }

  class EvaluationResult(val value: Double)

  override fun getSearchProviderId(): String = javaClass.name
  override fun getGroupName(): String = LangBundle.message("search.everywhere.calculator.group.name")
  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = false

  override fun fetchWeightedElements(pattern: String,
                                     progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<EvaluationResult>>) {
    val result = try {
      evaluate(pattern)
    }
    catch (_: Throwable) {
      return
    }
    consumer.process(FoundItemDescriptor(EvaluationResult(result), 0x8000))
  }

  override fun getDataForItem(element: EvaluationResult, dataId: String): Any? = null

  override fun processSelectedItem(selected: EvaluationResult, modifiers: Int, searchText: String): Boolean {
    CopyPasteManager.getInstance().setContents(StringSelection(selected.value.toString()))
    return true
  }

  override fun getElementsRenderer(): ListCellRenderer<EvaluationResult> = renderer

  private val renderer: ListCellRenderer<EvaluationResult> = run {
    val panel = JPanel(BorderLayout())
    val resultComponent = SimpleColoredComponent()
    val shortcutComponent = SimpleColoredComponent()
    panel.add(resultComponent, BorderLayout.CENTER)
    panel.add(shortcutComponent, BorderLayout.EAST)

    val sideGap = if (UIUtil.isUnderWin10LookAndFeel()) 0 else JBUIScale.scale(UIUtil.getListCellHPadding())
    panel.border = JBUI.Borders.empty(1, sideGap)
    ListCellRenderer { list, item, _, isSelected, _ ->
      resultComponent.clear()
      resultComponent.icon = AllIcons.Debugger.EvaluateExpression
      val foreground = if (isSelected) list.selectionForeground else list.foreground
      val attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground)
      resultComponent.append(LangBundle.message("search.everywhere.calculator.result.0", item.value.toString()), attributes)

      shortcutComponent.clear()
      val shortcutText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
      val shortcutAttributes = if (isSelected) {
        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.selectionForeground)
      }
      else {
        SimpleTextAttributes.GRAY_ATTRIBUTES
      }
      shortcutComponent.append(LangBundle.message("search.everywhere.calculator.shortcut.0", shortcutText), shortcutAttributes)

      panel.background = UIUtil.getListBackground(isSelected, true)
      panel.font = list.font
      panel
    }
  }

  /**
   * Converted to Kotlin from Java code taken here: https://stackoverflow.com/a/26227947/1417609
   *
   * Grammar:
   * expression = term
   *            | expression `+` term
   *            | expression `-` term
   * term       = factor
   *            | term `*` factor
   *            | term `/` factor
   * factor     = `+` factor
   *            | `-` factor
   *            | primary
   * primary    = `(` expression `)`
   *            | number
   *            | functionName factor
   *            | factor `^` factor
   */
  private fun evaluate(str: String): Double {
    return object : Any() {
      var pos = -1
      var ch: Char = Char.MIN_VALUE

      fun nextChar() {
        ch = str.getOrElse(++pos) { Char.MIN_VALUE }
      }

      fun eat(charToEat: Char): Boolean {
        while (ch == ' ') {
          nextChar()
        }
        if (ch == charToEat) {
          nextChar()
          return true
        }
        return false
      }

      fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
          when {
            eat('+') -> x += parseTerm() // addition
            eat('-') -> x -= parseTerm() // subtraction
            else -> return x
          }
        }
      }

      fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
          when {
            eat('*') -> x *= parseFactor() // multiplication
            eat('/') -> x /= parseFactor() // division
            else -> return x
          }
        }
      }

      fun parseFactor(): Double {
        return when {
          eat('+') -> parseFactor() // unary plus
          eat('-') -> -parseFactor() // unary minus
          else -> parsePrimary()
        }
      }

      fun parsePrimary(): Double {
        var x: Double
        if (eat('(')) { // parentheses
          x = parseExpression()
          eat(')')
        }
        else if (eat('0')) {
          x = when {
            eat('x') || eat('X') -> parseHex()
            eat('b') || eat('B') -> parseBinary()
            else -> parseDecimalOrOctal()
          }
        }
        else {
          val startPos = pos
          if (ch in '1'..'9' || ch == '.') { // numbers
            nextChar()
            while (ch in '0'..'9' || ch == '.') {
              nextChar()
            }
            x = str.substring(startPos, pos).toDouble()
          }
          else if (ch in 'a'..'z') { // functions
            while (ch in 'a'..'z') {
              nextChar()
            }
            val func = str.substring(startPos, pos)
            x = parseFactor()
            x = when (func) {
              "sqrt" -> sqrt(x)
              "sin" -> sin(Math.toRadians(x))
              "cos" -> cos(Math.toRadians(x))
              "tan" -> tan(Math.toRadians(x))
              else -> throw RuntimeException("Unknown function: $func")
            }
          }
          else {
            throw RuntimeException("Unexpected: $ch")
          }
        }
        if (eat('^')) {
          x = x.pow(parseFactor()) // exponentiation
        }
        return x
      }

      private fun parseHex(): Double {
        val startPos = pos
        while (ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F') {
          nextChar()
        }
        return parseLong(str.substring(startPos, pos), 16).toDouble()
      }

      private fun parseBinary(): Double {
        val startPos = pos
        while (ch == '0' || ch == '1') {
          nextChar()
        }
        return parseLong(str.substring(startPos, pos), 2).toDouble()
      }

      private fun parseDecimalOrOctal(): Double {
        val startPos = pos
        if (eat('.')) {
          while (ch in '0'..'9' || ch == '.') {
            nextChar()
          }
          return str.substring(startPos, pos).toDouble()
        }
        else {
          while (ch in '0'..'7') {
            nextChar()
          }
          return parseLong(str.substring(startPos, pos), 8).toDouble()
        }
      }

      fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) {
          throw RuntimeException("Unexpected: $ch")
        }
        return x
      }
    }.parse()
  }
}
