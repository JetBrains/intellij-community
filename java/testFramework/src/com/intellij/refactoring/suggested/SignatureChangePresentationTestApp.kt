// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.java.refactoring.suggested.JavaSuggestedRefactoringSupport
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import java.awt.*
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

private val refactoringSupport = JavaSuggestedRefactoringSupport()

private val model1 = run {
  val oldSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "String"),
    Parameter(1, "p2", "String"),
    Parameter(2, "p3", "String"),
    Parameter(3, "p4", "String")
  ), null)!!
  val newSignature = Signature.create("foo", "void", listOf(
    Parameter(3, "p4", "String"),
    Parameter(1, "p2", "String"),
    Parameter(0, "p1", "String"),
    Parameter(2, "p3", "String")
  ), null)!!
  refactoringSupport.ui.buildSignatureChangePresentation(oldSignature, newSignature)
}

private val model2 = run {
  val oldSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "String"),
    Parameter(1, "p2", "String"),
    Parameter(2, "p3", "String"),
    Parameter(3, "p4", "String"),
    Parameter(4, "p5", "String"),
    Parameter(5, "p6", "String"),
    Parameter(6, "p7", "int"),
    Parameter(7, "p8", "int")
  ), null)!!

  val newSignature = Signature.create("foo", "void", listOf(
    Parameter(3, "p4", "String"),
    Parameter(0, "p1", "String"),
    Parameter(2, "p3", "String"),
    Parameter(5, "p6", "String"),
    Parameter(4, "p5", "String"),
    Parameter(1, "p2", "String"),
    Parameter(7, "p8", "int"),
    Parameter(6, "p7", "int")
  ), null)!!
  refactoringSupport.ui.buildSignatureChangePresentation(oldSignature, newSignature)
}

private val model3 = run {
  val oldSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "String"),
    Parameter(1, "p2", "String"),
    Parameter(2, "p3", "String"),
    Parameter(3, "p4", "String")
  ), null)!!

  val newSignature = Signature.create("foo", "void", listOf(
    Parameter(1, "p2", "String"),
    Parameter(0, "p1", "String"),
    Parameter(3, "p4", "String"),
    Parameter(2, "p3", "String")
  ), null)!!
  refactoringSupport.ui.buildSignatureChangePresentation(oldSignature, newSignature)
}

private val model4 = run {
  val oldSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "int"),
    Parameter(1, "p2", "long"),
    Parameter(2, "p3", "int"),
    Parameter(3, "p4", "String"),
    Parameter(4, "p5", "int")
  ), null)!!

  val newSignature = Signature.create("foo", "void", listOf(
    Parameter(3, "p4", "String"),
    Parameter(0, "p1", "int"),
    Parameter(1, "p2", "long"),
    Parameter(2, "p3", "int")
  ), null)!!
  refactoringSupport.ui.buildSignatureChangePresentation(oldSignature, newSignature)
}

private val model5 = run {
  val oldSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "int"),
    Parameter(1, "p2", "long"),
    Parameter(2, "p3", "int"),
    Parameter(3, "p4", "String"),
    Parameter(4, "p5", "String")
  ), null)!!

  val newSignature = Signature.create("foo", "void", listOf(
    Parameter(0, "p1", "double"),
    Parameter(2, "p3", "int"),
    Parameter(1, "p2", "long"),
    Parameter(4, "p5New", "String"),
    Parameter(3, "p4", "String")
  ), null)!!
  refactoringSupport.ui.buildSignatureChangePresentation(oldSignature, newSignature)
}

fun main() {
  val font = Font("Courier New", Font.PLAIN, 12)
  val colorsScheme = EditorColorsSchemeImpl(null).apply {
    setAttributes(DiffColors.DIFF_MODIFIED, TextAttributes(null, Color.decode("0xCAD9FA"), null, null, Font.PLAIN))
    setAttributes(DiffColors.DIFF_INSERTED, TextAttributes(null, Color.decode("0xBEE6BE"), null, null, Font.PLAIN))
  }
  val presentation = SignatureChangePresentation(model2, font, colorsScheme, verticalMode = true)
  val frame = JFrame("Signature Change").apply {
    contentPane.add(JPanel(BorderLayout()).apply {
      border = EmptyBorder(10, 10, 10, 10)
      add(JLabel("Update usages to reflect signature changes:"), BorderLayout.NORTH)
      add(
        object : JComponent() {
          init {
            preferredSize = presentation.requiredSize
          }

          override fun paint(g: Graphics) {
            presentation.paint(g as Graphics2D, Rectangle(0, 0, width, height))
          }
        },
        BorderLayout.CENTER
      )
    })
  }
  frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
  frame.pack()
  frame.setLocation(400, 300)
  frame.isVisible = true
}