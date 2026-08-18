// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.icons.AllIcons
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.Border
import javax.swing.text.JTextComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that a form built with [FormPanel] is laid out exactly as the same form built with `panel {}`.
 *
 * The platform is its own reference. Both sides are laid out by the same
 * [com.intellij.ui.dsl.builder.impl.buildGridForm] and measured by the same grid, so nothing either of
 * them shares can break this test. What it reports is a form described wrongly: [FormGridLayout] reading a
 * row, a group or a comment off the children as something other than what the `panel {}` declaration beside
 * it says, which is the only thing the two sides still do separately.
 *
 * Each case is declared twice, once per DSL, out of the same Swing components, so what is compared is the
 * grid and nothing else. Components are paired by their type and the order they were declared in, which
 * the two declarations share.
 */
@TestApplication
class FormPanelParityTest {

  @Test
  fun rowsWithLabels() = assertParity(
    dsl = {
      row("Name:") { cell(newTextField()) }
      row("A rather longer label:") { cell(newTextField()) }
      row { cell(newCheckBox("A row with no label at all")) }
    },
    form = {
      FormRow("Name:") { TextFieldControl() }
      FormRow("A rather longer label:") { TextFieldControl() }
      FormRow { CheckBoxControl("A row with no label at all") }
    },
  )

  @Test
  fun rowsWithUnevenControlCounts() = assertParity(
    dsl = {
      row("One:") { cell(newTextField()) }
      row("Three:") {
        cell(newTextField())
        cell(newLabel("of"))
        cell(newTextField())
      }
      row("Two:") {
        cell(newTextField())
        cell(newTextField())
      }
    },
    form = {
      FormRow("One:") { TextFieldControl() }
      FormRow("Three:") {
        TextFieldControl()
        LabelControl("of")
        TextFieldControl()
      }
      FormRow("Two:") {
        TextFieldControl()
        TextFieldControl()
      }
    },
  )

  @Test
  fun controlsThatFillTheWidthTheyAreGiven() = assertParity(
    dsl = {
      row("Filled:") { cell(newTextField()).align(AlignX.FILL) }
      row { cell(newTextField()).align(AlignX.FILL) }
      row("Filled, then not:") {
        cell(newTextField()).align(AlignX.FILL)
        cell(newLabel("after"))
      }
    },
    form = {
      FormRow("Filled:") { TextFieldControl(SwingModifier.cell(fillWidth = true)) }
      FormRow { TextFieldControl(SwingModifier.cell(fillWidth = true)) }
      FormRow("Filled, then not:") {
        TextFieldControl(SwingModifier.cell(fillWidth = true))
        LabelControl("after")
      }
    },
  )

  @Test
  fun comments() = assertParity(
    dsl = {
      row("Port:") { cell(newTextField()).comment("0 picks a free port") }
      row("Host:") { cell(newTextField()) }
      row { cell(newCheckBox("Use a proxy")).comment("A comment under a checkbox steps in past its box") }
      row { cell(newCheckBox("No comment here")) }
    },
    form = {
      FormRow("Port:", comment = "0 picks a free port") { TextFieldControl() }
      FormRow("Host:") { TextFieldControl() }
      FormRow(comment = "A comment under a checkbox steps in past its box") { CheckBoxControl("Use a proxy") }
      FormRow { CheckBoxControl("No comment here") }
    },
  )

  @Test
  fun labelGapDependsOnWhatFollowsIt() = assertParity(
    dsl = {
      row("Mode:") { cell(newCheckBox("Brave")) }
      row("Text:") { cell(newTextField()) }
      row("Nothing after me:") { }
    },
    form = {
      FormRow("Mode:") { CheckBoxControl("Brave") }
      FormRow("Text:") { TextFieldControl() }
      FormRow("Nothing after me:") { }
    },
  )

  @Test
  fun groupsAndSeparators() = assertParity(
    dsl = {
      row { cell(newCheckBox("Before the group")) }
      group("Proxy") {
        row("Host:") { cell(newTextField()) }
        row("Port:") { cell(newTextField()) }
      }
      separator()
      group("Not indented", indent = false) {
        row { cell(newCheckBox("Flush with the group title")) }
      }
      row { cell(newCheckBox("After the groups")) }
    },
    form = {
      FormRow { CheckBoxControl("Before the group") }
      FormGroup("Proxy") {
        FormRow("Host:") { TextFieldControl() }
        FormRow("Port:") { TextFieldControl() }
      }
      FormSeparator()
      FormGroup("Not indented", indent = false) {
        FormRow { CheckBoxControl("Flush with the group title") }
      }
      FormRow { CheckBoxControl("After the groups") }
    },
  )

  @Test
  fun nestedGroups() = assertParity(
    dsl = {
      group("Outer") {
        row("Outer field:") { cell(newTextField()) }
        group("Inner") {
          row("Inner field:") { cell(newTextField()) }
        }
      }
    },
    form = {
      FormGroup("Outer") {
        FormRow("Outer field:") { TextFieldControl() }
        FormGroup("Inner") {
          FormRow("Inner field:") { TextFieldControl() }
        }
      }
    },
  )

  @Test
  fun indentedRows() = assertParity(
    dsl = {
      row { cell(newCheckBox("Enable")) }
      indent {
        row("Under the indent:") { cell(newTextField()) }
        row { cell(newCheckBox("Also indented")) }
        indent {
          row { cell(newCheckBox("Twice indented")) }
        }
      }
    },
    form = {
      FormRow { CheckBoxControl("Enable") }
      FormIndent {
        FormRow("Under the indent:") { TextFieldControl() }
        FormRow { CheckBoxControl("Also indented") }
        FormIndent {
          FormRow { CheckBoxControl("Twice indented") }
        }
      }
    },
  )

  @Test
  fun commentOnARowOfItsOwn() = assertParity(
    dsl = {
      row { cell(newCheckBox("Enable")) }
      row { comment("A comment on a row of its own, wrapped to the width of the form") }
      row("After:") { cell(newTextField()) }
    },
    form = {
      FormRow { CheckBoxControl("Enable") }
      FormComment("A comment on a row of its own, wrapped to the width of the form")
      FormRow("After:") { TextFieldControl() }
    },
  )

  /**
   * A comment beside a control takes the width it asks for, and the controls before it stand where they would
   * without it.
   *
   * `panel {}` gives such a comment the width the row has left, which is not reproduced: a row here holds only
   * the controls it was written with, where the row it replaces holds one comment per state and hides all but
   * one, so a comment that fills a middle column there fills to the end of the row here - and a row whose last
   * cell claims the rest of the width stands 3px narrower and shorter than the rows around it. A row that
   * wants the width says `cell(fillWidth = true)`.
   *
   * Which is why only the `panel {}` side names a line length: it fills whenever the length is
   * [com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP], so it has to be told not to. `Comment` keeps that
   * as its default and fills nothing, so there is nothing to opt out of on this side.
   */
  @Test
  fun commentsBesideAControl() = assertParity(
    dsl = {
      row {
        cell(newCheckBox("Enable"))
        comment("Short enough to leave room", maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row { cell(newTextField()) }
    },
    form = {
      FormRow {
        CheckBoxControl("Enable")
        Comment("Short enough to leave room")
      }
      FormRow { TextFieldControl() }
    },
  )

  /** An icon before the text it belongs to keeps a small gap, not the gap between two unrelated controls. */
  @Test
  fun aControlThatKeepsASmallGapFromTheNextOne() = assertParity(
    dsl = {
      row {
        cell(newTextField())
        icon(AllIcons.General.Error).gap(RightGap.SMALL)
        comment("Something is wrong with it", maxLineLength = DEFAULT_COMMENT_WIDTH)
      }
      row {
        cell(newTextField())
        icon(AllIcons.General.Error)
        cell(newLabel("a whole gap away"))
      }
    },
    form = {
      FormRow {
        TextFieldControl()
        IconControl(SwingModifier.cell(smallGapAfter = true))
        Comment("Something is wrong with it")
      }
      FormRow {
        TextFieldControl()
        IconControl()
        LabelControl("a whole gap away")
      }
    },
  )

  @Test
  fun rowsThatKeepTheirDistance() = assertParity(
    dsl = {
      row { cell(newCheckBox("Enable")) }.bottomGap(BottomGap.SMALL)
      row { cell(newLabel("First client")) }.topGap(TopGap.SMALL)
      row { cell(newTextField()) }
      row { cell(newLabel("Second client")) }.topGap(TopGap.SMALL)
      row { cell(newTextField()) }
      row { cell(newLabel("Set apart further")) }.topGap(TopGap.MEDIUM)
      row { cell(newTextField()) }
    },
    form = {
      FormRow(bottomGap = FormGap.SMALL) { CheckBoxControl("Enable") }
      FormRow(topGap = FormGap.SMALL) { LabelControl("First client") }
      FormRow { TextFieldControl() }
      FormRow(topGap = FormGap.SMALL) { LabelControl("Second client") }
      FormRow { TextFieldControl() }
      FormRow(topGap = FormGap.MEDIUM) { LabelControl("Set apart further") }
      FormRow { TextFieldControl() }
    },
  )

  @Test
  fun aGapAskedForOnBothSidesOfTheSameSeamIsTakenOnce() = assertParity(
    dsl = {
      row { cell(newCheckBox("Above")) }.bottomGap(BottomGap.SMALL)
      row { cell(newCheckBox("Below")) }.topGap(TopGap.SMALL)
      row("Nothing asked for here:") { cell(newTextField()) }
    },
    form = {
      FormRow(bottomGap = FormGap.SMALL) { CheckBoxControl("Above") }
      FormRow(topGap = FormGap.SMALL) { CheckBoxControl("Below") }
      FormRow("Nothing asked for here:") { TextFieldControl() }
    },
  )

  @Test
  fun rowsThatGiveUpTheDistanceTheyWouldOtherwiseKeep() = assertParity(
    dsl = {
      group("Group") {
        row { cell(newCheckBox("In the group")) }
      }
      row { cell(newCheckBox("Right under it")) }.topGap(TopGap.NONE)
    },
    form = {
      FormGroup("Group") {
        FormRow { CheckBoxControl("In the group") }
      }
      FormRow(topGap = FormGap.NONE) { CheckBoxControl("Right under it") }
    },
  )

  /**
   * The page margins go on the component that owns the grid, and a form given them is laid out exactly as
   * `panel {}` given the same ones - including the few pixels a row starting with a check box steps out to
   * the left, which is what the margins leave it room for.
   */
  @Test
  fun aFormGivenThePageMarginsStandsWhereTheDslDoes() = assertParity(
    margins = JBUI.Borders.empty(5, 16, 10, 16),
    dsl = {
      row { cell(newCheckBox("Enable")) }
      group("Clients", indent = false) {
        row { comment("Set up the clients that were detected") }
        indent {
          row { cell(newLabel("A client")) }
          row { cell(newTextField()) }
        }
      }
    },
    form = {
      FormRow { CheckBoxControl("Enable") }
      FormGroup("Clients", indent = false) {
        FormComment("Set up the clients that were detected")
        FormIndent {
          FormRow { LabelControl("A client") }
          FormRow { TextFieldControl() }
        }
      }
    },
  )

  @Test
  fun scrollPaneInAResizableRow() = assertParity(
    dsl = {
      row("Log:") { cell(newScrollPane()).align(AlignX.FILL) }.resizableRow()
      row("After:") { cell(newTextField()) }
    },
    form = {
      FormRow("Log:", resizable = true) { ScrollPaneControl(SwingModifier.cell(fillWidth = true)) }
      FormRow("After:") { TextFieldControl() }
    },
  )

  // --- The comparison ---------------------------------------------------------------------------

  /**
   * [margins] go on both panels, which is where the Settings dialog puts a page's: on the component that
   * owns the grid, so the grid can step out into them.
   */
  private fun assertParity(
    dsl: Panel.() -> Unit,
    form: @Composable FormScope.() -> Unit,
    margins: Border? = null,
  ) = runComposeSwingTest {
    setContent {
      FormPanel(modifier = SwingModifier.testTag(FORM_TAG), content = form)
    }

    val formPanel = onNodeWithTag(FORM_TAG).fetch<JComponent>()
    val dslPanel = panel(dsl)

    formPanel.border = margins
    dslPanel.border = margins

    layOut(dslPanel)
    layOut(formPanel)

    assertEquals(dslPanel.preferredSize, formPanel.preferredSize, "preferred size")
    assertEquals(dslPanel.minimumSize, formPanel.minimumSize, "minimum size")
    assertBoundsMatch(dslPanel, formPanel)

    val reference = dslPanel.captureToImage()
    assertWasDrawn(reference)
    assertImagesPixelPerfect(reference, formPanel.captureToImage())
  }

  /**
   * Asserts something was actually painted, so that comparing the two images means something: two blank
   * images are pixel-perfect against each other.
   */
  private fun assertWasDrawn(image: BufferedImage) {
    val first = image.getRGB(0, 0)
    val drawn = (0 until image.width).any { x -> (0 until image.height).any { y -> image.getRGB(x, y) != first } }
    assertTrue(drawn, "nothing was painted, so this comparison would pass whatever the form looked like")
  }

  /**
   * Gives [panel] the width a settings page would give it and more height than it asks for, so a resizable
   * row has space to take, then lays out the whole tree under it: a panel that was never realized in a
   * window is laid out only when asked, and only one level at a time.
   */
  private fun layOut(panel: JComponent) {
    panel.size = Dimension(WIDTH, panel.preferredSize.height + SPARE_HEIGHT)
    layOutTree(panel)
  }

  private fun layOutTree(component: Component) {
    if (component !is Container) return
    component.doLayout()
    component.components.forEach(::layOutTree)
  }

  /**
   * Compares the bounds of every component of the two panels, pairing them by kind and by the order they
   * were declared in.
   */
  private fun assertBoundsMatch(dslPanel: JComponent, formPanel: JComponent) {
    val expected = dslPanel.components.groupBy(::kindOf)
    // A row boundary is bookkeeping rather than a component of the form: it is given no cell, so it has no
    // bounds to compare and the screenshot the two sides are also held to is what says it draws nothing.
    val actual = formPanel.components
      .filterNot { it is FormRowBoundaryComponent }
      .groupBy(::kindOf)

    assertEquals(expected.keys, actual.keys, "the two forms hold different kinds of component")
    for ((kind, expectedOfKind) in expected) {
      val actualOfKind = actual.getValue(kind)
      assertEquals(expectedOfKind.size, actualOfKind.size, "number of ${kind.simpleName}s")
      for ((index, component) in expectedOfKind.withIndex()) {
        assertEquals(
          component.bounds,
          actualOfKind[index].bounds,
          "bounds of ${kind.simpleName} #$index (${describe(component)})",
        )
      }
    }
  }

  /**
   * What a component counts as for pairing. `panel {}` builds its titled separator as an anonymous
   * subclass, so the class a component was declared with is the first one that has a name.
   */
  private fun kindOf(component: Component): Class<*> {
    var kind: Class<*> = component.javaClass
    while (kind.isAnonymousClass) kind = kind.superclass
    return kind
  }

  /** A component carrying no text of its own - an icon - is named by its type. */
  private fun describe(component: Component): String =
    when (component) {
      is JLabel -> component.text
      is JCheckBox -> component.text
      is JTextComponent -> component.text
      else -> null
    } ?: component.javaClass.simpleName

  // --- The components both sides are built out of -----------------------------------------------

  private fun newTextField(): JTextField = JTextField(20)

  private fun newCheckBox(text: String): JCheckBox = JCheckBox(text)

  private fun newLabel(text: String): JLabel = JLabel(text)

  private fun newScrollPane(): JScrollPane = JScrollPane(JTextArea(4, 20))

  /** What `panel {}`'s `icon(...)` builds, so the two sides compare the same component. */
  private fun newIcon(): JLabel = JBLabel(AllIcons.General.Error)

  @Composable
  private fun TextFieldControl(modifier: SwingModifier = SwingModifier) =
    SwingNode(factory = { newTextField() }, update = { applyModifier(modifier) })

  @Composable
  private fun CheckBoxControl(text: String, modifier: SwingModifier = SwingModifier) =
    SwingNode(factory = { newCheckBox(text) }, update = { applyModifier(modifier) })

  @Composable
  private fun LabelControl(text: String, modifier: SwingModifier = SwingModifier) =
    SwingNode(factory = { newLabel(text) }, update = { applyModifier(modifier) })

  @Composable
  private fun ScrollPaneControl(modifier: SwingModifier = SwingModifier) =
    SwingNode(factory = { newScrollPane() }, update = { applyModifier(modifier) })

  @Composable
  private fun IconControl(modifier: SwingModifier = SwingModifier) =
    SwingNode(factory = { newIcon() }, update = { applyModifier(modifier) })
}

private const val FORM_TAG = "form-under-test"

/** The width a settings page gives its content; wide enough for a filled cell to have room to fill. */
private const val WIDTH = 500

/** Height beyond what a form asks for, so a resizable row has something to take. */
private const val SPARE_HEIGHT = 60
