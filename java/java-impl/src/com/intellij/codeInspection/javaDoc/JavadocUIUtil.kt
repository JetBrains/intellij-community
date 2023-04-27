// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.java.JavaBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import kotlin.reflect.KMutableProperty0

data class MasterDetailsItem(@NlsContexts.Checkbox val text: String, val checkboxBinding: MutableProperty<Boolean>, val description: JComponent)

object JavadocUIUtil {

  private fun Cell<JBCheckBox>.bindCheckbox(get: () -> Boolean, set: (Boolean) -> Unit): Cell<JBCheckBox> = applyToComponent {
    isSelected = get()
    addActionListener {
      set(isSelected)
    }
  }

  private fun Cell<JBCheckBox>.bindCheckbox(property: KMutableProperty0<Boolean>): Cell<JBCheckBox> = bindCheckbox(property::get, property::set)

  fun <T> Cell<ComboBox<T>>.bindItem(property: KMutableProperty0<T>): Cell<ComboBox<T>> = applyToComponent {
    selectedItem = property.get()
    addActionListener {
      @Suppress("UNCHECKED_CAST")
      property.set(selectedItem as T)
    }
  }

  fun item(@NlsContexts.Checkbox text: String, checkboxBinding: KMutableProperty0<Boolean>, description: JComponent): MasterDetailsItem {
    return MasterDetailsItem(text, MutableProperty(checkboxBinding::get, checkboxBinding::set), description)
  }

  fun javadocDeclarationOptions(settings: JavadocDeclarationInspection): JComponent = panel {
    row {
      expandableTextField()
        .label(JavaBundle.message("inspection.javadoc.label.text"), LabelPosition.TOP)
        .align(AlignX.FILL)
        .applyToComponent {
          text = settings.ADDITIONAL_TAGS
          document.addDocumentListener(object: DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
              settings.ADDITIONAL_TAGS = text.trim()
            }
          })
        }
    }
    row {
      checkBox(JavaBundle.message("inspection.javadoc.option.ignore.throws"))
        .bindCheckbox(settings::IGNORE_THROWS_DUPLICATE)
    }
    row {
      checkBox(JavaBundle.message("inspection.javadoc.option.ignore.period"))
        .bindCheckbox(settings::IGNORE_PERIOD_PROBLEM)
    }
    row {
      checkBox(JavaBundle.message("inspection.javadoc.option.ignore.self.ref"))
        .bindCheckbox(settings::IGNORE_SELF_REFS)
    }
    row {
      checkBox(JavaBundle.message("inspection.javadoc.option.ignore.deprecated"))
        .bindCheckbox(settings::IGNORE_DEPRECATED_ELEMENTS)
    }
  }
}
