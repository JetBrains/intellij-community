// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.accessibility

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.Graphics
import javax.accessibility.*
import javax.swing.*

@Suppress("DialogTitleCapitalization")
internal class AccessibilityFailedInspectionsPanel : UISandboxPanel {
  override val title: String = "Failed inspections"
  override fun createContent(disposable: Disposable): JComponent = panel {
    row {
      text("This page shows examples of components with accessibility problems and is intended to test the accessibility audit feature in the UI Inspector.")
    }
    group("Accessible state set contains focusable") {
      twoColumnsRow(
        {
          button("Not focusable button") {}.applyToComponent {
            isFocusable = false
          }
        }, {
          button("Normal button") {}
        })
    }

    group("Accessible action and value are not null") {
      twoColumnsRow(
        {
          cell(object : JCheckBox("Broken checkbox") {
            override fun getAccessibleContext() = object : AccessibleJCheckBox() {
              override fun getAccessibleAction(): AccessibleAction? = null
              override fun getAccessibleValue(): AccessibleValue? = null
            }
          })
        }, {
          checkBox("Normal checkbox")
        })
    }

    group("Accessible name and description are not equal") {
      twoColumnsRow(
        {
          button("Broken button") {}.applyToComponent {
            accessibleContext.accessibleDescription = "button"
            accessibleContext.accessibleName = "button"
          }
        }, {
          button("Normal Button") {}
        })
    }

    group("Accessible value is not null") {
      twoColumnsRow(
        {
          cell(object : JProgressBar() {
            override fun getAccessibleContext() = object : AccessibleJProgressBar() {
              override fun getAccessibleValue(): AccessibleValue? = null
            }
          }).label("Broken progress bar", LabelPosition.TOP)
        }, {
          cell(JProgressBar())
            .label("Normal progress bar", LabelPosition.TOP)
        })
    }

    group("Multiple Failed Inspections") {
      twoColumnsRow(
        {
          cell(object : JTextField() {
            override fun getAccessibleContext() = object : AccessibleJTextField() {
              override fun getAccessibleText(): AccessibleText? = null
              override fun getAccessibleEditableText(): AccessibleEditableText? = null
              override fun getAccessibleName(): String? = null
            }

          }).label("Broken text field", LabelPosition.TOP)
        }, {
          textField().label("Normal text field", LabelPosition.TOP)
        })
    }


    group("Component with icon has non-default accessible name") {
      twoColumnsRow(
        {
          cell(JLabel("Some info").apply {
            icon = AllIcons.General.Error
          }).label("JLabel with default accessible name", LabelPosition.TOP)
        }, {
          cell(JLabel("Some info").apply {
            icon = AllIcons.General.Error
            accessibleContext.accessibleName = "Error: Some info"
          }).label("JLabel with custom accessible name", LabelPosition.TOP)
        })

      twoColumnsRow(
        {
          cell(SimpleColoredComponent().apply {
            icon = AllIcons.General.Error
            append("Some info")
          }).label("SimpleColoredComponent with default name", LabelPosition.TOP)
        }, {
          cell(object : SimpleColoredComponent() {
            override fun getAccessibleContext() = object : AccessibleSimpleColoredComponent() {
              override fun getAccessibleName(): String = "Error: Some info"
            }
          }.apply {
            icon = AllIcons.General.Error
            append("Some info")
          }).label("SimpleColoredComponent with custom name", LabelPosition.TOP)
        })
    }

    group("Implements Accessible") {
      twoColumnsRow(
        {
          cell(object : JComponent() {
            override fun paintComponent(g: Graphics) {
              super.paintComponent(g)
              g.color = background
              g.fillRect(0, 0, width, height)
            }
          }.apply {
            preferredSize = Dimension(100, 50)
            background = JBColor.RED
          }).label("Not Accessible component", LabelPosition.TOP)
        }, {
          cell(object : JComponent(), Accessible {
            override fun getAccessibleContext() = object : AccessibleJComponent() {
              override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL
            }

            override fun paintComponent(g: Graphics) {
              super.paintComponent(g)
              g.color = background
              g.fillRect(0, 0, width, height)
            }
          }.apply {
            preferredSize = Dimension(100, 50)
            background = JBColor.GREEN
          }).label("Accessible component", LabelPosition.TOP)
        })
    }
  }
}
