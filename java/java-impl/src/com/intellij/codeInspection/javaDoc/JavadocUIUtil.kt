// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.java.JavaBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import kotlin.reflect.KMutableProperty0

data class MasterDetailsItem(@NlsContexts.Checkbox val text: String, val checkboxBinding: PropertyBinding<Boolean>, val description: JComponent)

object JavadocUIUtil {

  fun Cell<JBCheckBox>.bindCheckbox(get: () -> Boolean, set: (Boolean) -> Unit): Cell<JBCheckBox> = applyToComponent {
    isSelected = get()
    addActionListener {
      set(isSelected)
    }
  }

  fun Cell<JBCheckBox>.bindCheckbox(property: KMutableProperty0<Boolean>): Cell<JBCheckBox> = bindCheckbox(property::get, property::set)

  fun <T> Cell<ComboBox<T>>.bindItem(property: KMutableProperty0<T>): Cell<ComboBox<T>> = applyToComponent {
    selectedItem = property.get()
    addActionListener {
      @Suppress("UNCHECKED_CAST")
      property.set(selectedItem as T)
    }
  }

  fun item(@NlsContexts.Checkbox text: String, checkboxBinding: KMutableProperty0<Boolean>, description: JComponent): MasterDetailsItem {
    return MasterDetailsItem(text, PropertyBinding(checkboxBinding::get, checkboxBinding::set), description)
  }

  fun createMasterDetails(items: List<MasterDetailsItem>): JPanel {
    val layout = CardLayout()
    val description = JPanel(layout)
    val list = CheckBoxList<MasterDetailsItem>()
    list.border = JBUI.Borders.empty(2)
    items.forEach { item ->
      description.add(item.description, item.text)
      list.addItem(item, item.text, item.checkboxBinding.get())
    }
    list.addListSelectionListener {
      val selectedIndex = list.selectedIndex
      val item = list.takeIf { selectedIndex >= 0 }?.getItemAt(selectedIndex) ?: return@addListSelectionListener
      layout.show(description, item.text)
      UIUtil.setEnabled(description, item.checkboxBinding.get(), true)
    }
    list.setCheckBoxListListener(
      CheckBoxListListener { _, value ->
        val selectedIndex = list.selectedIndex
        val item = list.takeIf { selectedIndex >= 0 }?.getItemAt(selectedIndex) ?: return@CheckBoxListListener
        item.checkboxBinding.set(value)
        UIUtil.setEnabled(description, value, true)
      }
    )
    list.selectedIndex = 0

    return panel {
      row {
        cell(list).verticalAlign(VerticalAlign.FILL)
        cell(description).horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.TOP)
      }
    }
  }

  fun javadocDeclarationOptions(settings: JavadocDeclarationInspection): JComponent = panel {
    row {
      expandableTextField()
        .label(JavaBundle.message("inspection.javadoc.label.text"), LabelPosition.TOP)
        .horizontalAlign(HorizontalAlign.FILL)
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
  }

  fun missingJavadocOptions(settings: MissingJavadocInspection): JComponent {
    return panel {
      row {
        checkBox(JavaBundle.message("inspection.javadoc.option.ignore.deprecated"))
          .bindCheckbox(settings::IGNORE_DEPRECATED_ELEMENTS)
      }
      row {
        checkBox(JavaBundle.message("inspection.javadoc.option.ignore.simple"))
          .bindCheckbox(settings::IGNORE_ACCESSORS)
      }
      row {
        topGap(TopGap.SMALL)
        cell(createMasterDetails(listOf(
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title.package"),
            settings.PACKAGE_SETTINGS::ENABLED,
            createOptions(
              settings.PACKAGE_SETTINGS,
              emptyList(),
              listOf("@author", "@version", "@since")
            )
          ),
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title.module"),
            settings.MODULE_SETTINGS::ENABLED,
            createOptions(
              settings.MODULE_SETTINGS,
              emptyList(),
              listOf("@author", "@version", "@since")
            )
          ),
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title"),
            settings.TOP_LEVEL_CLASS_SETTINGS::ENABLED,
            createOptions(
              settings.TOP_LEVEL_CLASS_SETTINGS,
              listOf(JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PACKAGE_LOCAL),
              listOf("@author", "@version", "@since", "@param")
            )
          ),
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title.method"),
            settings.METHOD_SETTINGS::ENABLED,
            createOptions(
              settings.METHOD_SETTINGS,
              listOf(JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED,
                     JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE),
              listOf("@return", "@param", JavaBundle.message("inspection.javadoc.throws.or.exception.option"))
            )
          ),
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title.field"),
            settings.FIELD_SETTINGS::ENABLED,
            createOptions(
              settings.FIELD_SETTINGS,
              listOf(JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED,
                     JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE),
              emptyList()
            )
          ),
          item(
            JavaBundle.message("inspection.javadoc.option.tab.title.inner.class"),
            settings.INNER_CLASS_SETTINGS::ENABLED,
            createOptions(
              settings.INNER_CLASS_SETTINGS,
              listOf(JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED,
                     JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE),
              emptyList()
            )
          )
        )))
      }
    }
  }

  private fun createOptions(options: MissingJavadocInspection.Options, visibility: List<@NlsSafe String>, tags: List<@NlsSafe String>): JComponent {
    return panel {
      if (visibility.isNotEmpty()) {
        row {
          comboBox(visibility)
            .bindItem(options::MINIMAL_VISIBILITY)
            .label(JavaBundle.message("inspection.missingJavadoc.label.minimalVisibility"), LabelPosition.TOP)
          bottomGap(BottomGap.SMALL)
        }
      }
      if (tags.isNotEmpty()){
        row {
          label(JavaBundle.message("inspection.missingJavadoc.label.requiredTags"))
        }
        indent {
          tags.forEach { tag ->
            row {
              checkBox(tag).bindCheckbox({ options.isTagRequired(tag) }, { value -> options.setTagRequired(tag, value) })
            }
          }
        }
      }
    }
  }
}
