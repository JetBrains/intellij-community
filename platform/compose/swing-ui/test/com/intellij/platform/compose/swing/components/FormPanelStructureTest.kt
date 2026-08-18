// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Drives one [FormPanel] through changes of structure - rows and groups appearing, disappearing and
 * changing places - and asserts that the form is laid out by what it currently holds and by nothing it
 * used to hold, and that the components come through untouched.
 *
 * A grid never gives up a sub-grid, and every row with a label has one, so a form that adjusted its grid in
 * place would sooner or later try to fill a cell it had already filled - or lay a row out where an older row
 * used to be. Each structure here is reached by more than one route, and the layout it produces has to be
 * the same every time it is reached, whatever the form held in between.
 */
class FormPanelStructureTest {

  @Test
  fun aStructureIsLaidOutTheSameHoweverItIsReached() = runComposeSwingTest {
    val middle = mutableStateOf(false)
    val last = mutableStateOf(false)

    setContent {
      Form {
        FormRow("First:") { TextFieldControl("first") }
        if (middle.value) FormRow("Middle:") { TextFieldControl("middle") }
        if (last.value) FormRow("A much longer last label:") { TextFieldControl("last") }
      }
    }

    val seen = mutableMapOf<String, List<String>>()
    // Grow into each structure, then shrink back out of it, so every one is reached from both directions.
    val route = listOf(
      false to false, true to false, true to true, false to true,
      false to false, true to true, true to false, false to false,
    )
    for ((showMiddle, showLast) in route) {
      middle.value = showMiddle
      last.value = showLast
      awaitIdle()

      val structure = "middle = $showMiddle, last = $showLast"
      val layout = form().layOut().describeComponents()
      val first = seen.putIfAbsent(structure, layout)
      if (first != null) {
        assertEquals(first, layout, "the form was laid out differently the second time it held: $structure")
      }
    }

    assertNotEquals(
      seen.getValue("middle = false, last = false"),
      seen.getValue("middle = true, last = true"),
      "the two structures should not lay out identically, or this test proves nothing",
    )
  }

  @Test
  fun reorderingRowsMovesThemAndNothingElse() = runComposeSwingTest {
    val names = listOf("alpha", "beta", "gamma")
    val order = mutableStateOf(names)

    setContent {
      Form {
        order.value.forEach { name ->
          key(name) { FormRow("$name:") { TextFieldControl(name) } }
        }
      }
    }

    val fields = names.associateWith { onNodeWithTag(it).fetch<JTextField>() }
    val original = form().layOut().describeComponents()

    order.value = listOf("gamma", "alpha", "beta")
    awaitIdle()
    val rotated = form().layOut().describeComponents()
    assertNotEquals(original, rotated, "reordering rows moves them")

    order.value = names
    awaitIdle()
    assertEquals(original, form().layOut().describeComponents(), "restoring the order restores the layout")
    for ((name, field) in fields) {
      assertSame(field, onNodeWithTag(name).fetch<JTextField>(), "'$name' is still held by the same component")
    }
  }

  @Test
  fun aGroupComingBackLandsWhereItWas() = runComposeSwingTest {
    val showGroup = mutableStateOf(true)

    setContent {
      Form {
        FormRow("Outside:") { TextFieldControl("outside") }
        if (showGroup.value) {
          FormGroup("Proxy") {
            FormRow("Host:") { TextFieldControl("host") }
            FormRow("Port:") { TextFieldControl("port") }
          }
        }
      }
    }

    val withGroup = form().layOut().describeComponents()

    showGroup.value = false
    awaitIdle()
    val withoutGroup = form().layOut().describeComponents()
    assertTrue(withoutGroup.size < withGroup.size, "the group's rows are gone")

    showGroup.value = true
    awaitIdle()
    assertEquals(withGroup, form().layOut().describeComponents())
  }

  @Test
  fun aTextFieldKeepsWhatItHoldsAcrossAChangeOfStructure() = runComposeSwingTest {
    val showFirst = mutableStateOf(false)

    setContent {
      Form {
        if (showFirst.value) FormRow("Inserted above:") { TextFieldControl("inserted") }
        FormRow("Typed into:") { TextFieldControl("typed") }
      }
    }

    val field = onNodeWithTag("typed").fetch<JTextField>()
    field.text = "what the user typed"
    field.caretPosition = 4

    showFirst.value = true
    awaitIdle()

    // The form is laid out again around the field; the field itself is never taken out of it, which is what
    // leaves the text and the caret - and the focus, which follows the component - where the user left them.
    assertSame(field, onNodeWithTag("typed").fetch<JTextField>())
    assertEquals("what the user typed", field.text)
    assertEquals(4, field.caretPosition)
  }

  @Test
  fun hidingARowCollapsesItWithoutTakingItOut() = runComposeSwingTest {
    val visible = mutableStateOf(true)

    setContent {
      Form {
        FormRow("Always:") { TextFieldControl("always") }
        // A row collapses when everything in it is hidden, so this one holds nothing else - a label of its
        // own would still be shown, and the row would keep the height of the label.
        FormRow { TextFieldControl("sometimes", SwingModifier.visible(visible.value)) }
      }
    }

    val heightWithRow = form().layOut().preferredSize.height
    val hidden = onNodeWithTag("sometimes").fetch<JTextField>()

    visible.value = false
    awaitIdle()

    assertTrue(form().layOut().preferredSize.height < heightWithRow, "a hidden row takes no height")
    assertSame(hidden, onNodeWithTag("sometimes").fetch<JTextField>(), "the row is hidden, not taken out")
  }

  @Test
  fun changingWhatARowAsksForLaysItOutAgainWithoutTakingAnythingOut() = runComposeSwingTest {
    val resizable = mutableStateOf(false)

    setContent {
      Form {
        FormRow("Log:", resizable = resizable.value) { TextFieldControl("log") }
      }
    }

    val field = onNodeWithTag("log").fetch<JTextField>()
    // Nothing is added or removed here - the row says something different about the components it already
    // holds - so the form has to notice the row itself changing, not just its children coming and going.
    val packed = form().layOut(spareHeight = 100).describeComponents()

    resizable.value = true
    awaitIdle()

    assertNotEquals(packed, form().layOut(spareHeight = 100).describeComponents(), "the row takes the height it now asks for")
    assertSame(field, onNodeWithTag("log").fetch<JTextField>(), "and keeps the component it was already holding")
  }

  @Test
  fun changingWhatAControlAsksOfItsCellLaysItOutAgainWithoutTakingItOut() = runComposeSwingTest {
    val fill = mutableStateOf(false)

    setContent {
      Form {
        FormRow("Log:") {
          TextFieldControl("log", if (fill.value) SwingModifier.cell(fillWidth = true) else SwingModifier)
        }
      }
    }

    val field = onNodeWithTag("log").fetch<JTextField>()
    val asked = form().layOut().describeComponents()

    fill.value = true
    awaitIdle()

    assertNotEquals(asked, form().layOut().describeComponents(), "the control takes the width it now asks for")
    assertSame(field, onNodeWithTag("log").fetch<JTextField>(), "and is the component it already was")
  }

  // --- Helpers ----------------------------------------------------------------------------------

  /** The form under test, tagged so it can be told from the root the harness composes it into. */
  @Composable
  private fun Form(content: @Composable FormScope.() -> Unit) {
    FormPanel(modifier = SwingModifier.testTag(FORM_TAG), content = content)
  }

  private fun ComposeSwingTest.form(): JComponent = onNodeWithTag(FORM_TAG).fetch<JComponent>()

  /** Gives the form the width a settings page would, and [spareHeight] beyond what it asks for. */
  private fun JComponent.layOut(spareHeight: Int = 0): JComponent {
    size = Dimension(WIDTH, preferredSize.height + spareHeight)
    layOutTree(this)
    return this
  }

  private fun layOutTree(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layOutTree)
  }

  /** Where every component of the form ended up, as something a failure can print. */
  private fun JComponent.describeComponents(): List<String> =
    components.map { "${it.javaClass.simpleName} ${describe(it)} at ${it.bounds}" }

  private fun describe(component: Component): String =
    when (component) {
      is javax.swing.JLabel -> "'${component.text}'"
      is JTextField -> "'${component.name}'"
      else -> ""
    }

  @Composable
  private fun TextFieldControl(tag: String, modifier: SwingModifier = SwingModifier) {
    SwingNode(
      factory = { JTextField(20) },
      update = { applyModifier(modifier.testTag(tag).name(tag)) },
    )
  }
}

private const val FORM_TAG = "form-under-test"
private const val WIDTH = 500
