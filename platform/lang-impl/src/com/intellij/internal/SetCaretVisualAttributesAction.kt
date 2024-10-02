// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxWithColorChooser
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus

internal class SetCaretVisualAttributesAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val carets = editor.caretModel.allCarets
    if (carets.isEmpty()) return

    val dialog = CaretVisualAttributesDialog(project, carets.first().visualAttributes)
    if (dialog.showAndGet()) {
      val attributes = dialog.attributes
      for (caret in carets) {
        caret.visualAttributes = attributes
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = e.project != null
                             && e.getData(CommonDataKeys.EDITOR) is EditorImpl
  }
}

@ApiStatus.Internal
@Suppress("DEPRECATION")
class CaretVisualAttributesDialog(project: Project, attributes: CaretVisualAttributes?) : DialogWrapper(project) {
  private val colorChooser: CheckBoxWithColorChooser
  private var weight: CaretVisualAttributes.Weight = attributes?.weight ?: CaretVisualAttributes.Weight.NORMAL
  private var shape: CaretVisualAttributes.Shape = attributes?.shape ?: CaretVisualAttributes.Shape.DEFAULT
  private var thickness: Float = attributes?.thickness ?: 1.0f

  init {
    title = "Set CaretVisualAttributes"
    colorChooser = CheckBoxWithColorChooser("  ", attributes?.color != null, attributes?.color)
    init()
  }

  val attributes: CaretVisualAttributes
    get() = CaretVisualAttributes(colorChooser.color, weight, shape, thickness)

  override fun createCenterPanel(): DialogPanel = panel {
    row("Colour:") { cell(colorChooser) }
    row("Weight:") {
      comboBox(EnumComboBoxModel(CaretVisualAttributes.Weight::class.java))
        .bindItem(::weight.toNullableProperty())
    }
    row("Shape:") {
      comboBox(EnumComboBoxModel(CaretVisualAttributes.Shape::class.java))
        .bindItem(::shape.toNullableProperty())
    }
    row("Thickness:") {
      floatTextField({ thickness }, { thickness = it }, 0f, 1.0f)
        .align(AlignX.FILL)
    }
  }

  private fun Row.floatTextField(getter: () -> Float, setter: (Float) -> Unit, min: Float, max: Float): Cell<JBTextField> {
    return textField()
      .bindText({ getter().toString() }, { value -> value.toFloatOrNull()?.let { floatValue -> setter(floatValue.coerceIn(min, max)) } })
      .validationOnInput {
        val value = it.text.toFloatOrNull()
        when {
          value == null -> error(UIBundle.message("please.enter.a.number"))
          value < min || value > max -> error(UIBundle.message("please.enter.a.number.from.0.to.1", min, max))
          else -> null
        }
      }
  }
}
