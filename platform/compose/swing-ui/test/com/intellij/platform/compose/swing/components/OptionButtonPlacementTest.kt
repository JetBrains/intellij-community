// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.test.assertEquals

/**
 * A button whose options arrive after the form was first laid out stands where a button that had them all
 * along stands.
 *
 * [JBOptionButton] reserves room for its focus ring only once it has options, and a form aligns a component by
 * the room it reserves. Read off the component, that reserve would depend on when the options arrived: a row
 * would shift as they loaded, and rows whose options loaded either side of a rebuild would not line up.
 */
class OptionButtonPlacementTest {

  @Test
  fun aButtonStandsWhereItStandsWhateverItsOptions() {
    // The IDE look and feel is what gives JBOptionButton its UI, and the reserve under test is the same
    // whichever theme that look and feel carries.
    UIManager.setLookAndFeel(DarculaLaf())
    val positions = mutableMapOf<String, Int>()
    for (withOptions in listOf(false, true)) {
      runComposeSwingTest {
        setContent {
          FormPanel(modifier = SwingModifier.testTag(TAG)) {
            FormRow { OptionButton(text = "Auto-Configure", options = options(withOptions)) }
          }
        }
        val form = onNodeWithTag(TAG).fetch<JComponent>()
        form.size = Dimension(400, form.preferredSize.height)
        layOutTree(form)
        val button = collect(form).filterIsInstance<JBOptionButton>().single()
        positions[if (withOptions) "with options" else "without options"] = button.x
      }
    }
    assertEquals(
      positions.getValue("without options"),
      positions.getValue("with options"),
      "the button moved when it was given options: $positions",
    )
  }

  private fun options(any: Boolean): List<AnAction> =
    if (!any) emptyList()
    else List(3) { i -> object : AnAction("Option $i") { override fun actionPerformed(e: AnActionEvent) {} } }

  private fun collect(c: Component): List<Component> =
    if (c is Container) listOf(c) + c.components.flatMap(::collect) else listOf(c)

  private fun layOutTree(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layOutTree)
  }
}

private const val TAG = "option-button-form"
