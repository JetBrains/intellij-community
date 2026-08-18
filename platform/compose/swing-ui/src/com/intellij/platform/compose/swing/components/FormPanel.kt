// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.intellij.BundleBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.ui.TitledSeparator as IdeaTitledSeparator

/**
 * A form: rows with aligned labels, comments, groups, separators and indents, measured by the platform's
 * own [com.intellij.ui.dsl.gridLayout.GridLayout] - the engine
 * [com.intellij.ui.dsl.builder.panel] measures with, so a form looks like every other settings page.
 *
 * A row holds the controls emitted inside it, and structure is ordinary Kotlin, so a row behind an `if`
 * comes and goes with the condition and the rows below it move up:
 *
 * ```
 * FormPanel {
 *     FormRow("Name:") { TextField(name, onValueChange = { name = it }) }
 *     FormRow("Port:", comment = "0 picks a free port") { Spinner(port, onValueChange = { port = it }) }
 *     FormGroup("Proxy") {
 *         FormRow { CheckBox("Use a proxy", useProxy, onValueChange = { useProxy = it }) }
 *         if (useProxy) {
 *             FormRow("Host:") { TextField(host, onValueChange = { host = it }) }
 *         }
 *     }
 *     FormRow("Log:", resizable = true) { LogView(SwingModifier.cell(fillWidth = true)) }
 * }
 * ```
 *
 * No row holds coordinates of its own. A row says what it is - a label, the controls after it, a comment
 * under them - and the form works the columns, the spans and the space between rows out from all of its
 * rows together, the way a layout works out where its children go. Inserting a row therefore moves the
 * rows below it and touches none of their components: what they hold, and the focus and the selection in
 * them, is unaffected.
 *
 * Rows generated from a list that reorders want [androidx.compose.runtime.key] around them, as anywhere
 * else in a composition; rows written out one by one need nothing.
 *
 * Coming from [com.intellij.ui.dsl.builder.panel]:
 *
 * | Kotlin UI DSL | here |
 * |---|---|
 * | `row("Name:") { textField() }` | `FormRow("Name:") { TextField(name, ::setName) }` |
 * | `cell(c).align(AlignX.FILL)` | `C(SwingModifier.cell(fillWidth = true))` |
 * | `.comment("...")`, `.rowComment("...")` | `FormRow(comment = "...")` |
 * | `.resizableRow()` | `FormRow(resizable = true)` |
 * | `.topGap(TopGap.SMALL)`, `.bottomGap(...)` | `FormRow(topGap = FormGap.SMALL)`, `bottomGap = ...` |
 * | `group("Title") { }`, `indent { }`, `separator()` | `FormGroup`, `FormIndent`, `FormSeparator` |
 * | `row { comment("...") }` | `FormComment("...")` |
 * | `.bindText(::property)`, `.bindSelected(...)` | state in, callback out; see `SettingState` |
 * | `.visibleIf(predicate)`, `.enabledIf(...)` | an `if` around the row, or a `SwingModifier` |
 *
 * Right and centre alignment, columns that take the spare width and wider gaps between controls are not
 * offered yet; a row that needs one of them stays with `panel {}` for now.
 *
 * A form says what its rows are and `com.intellij.ui.dsl.builder.impl.buildGridForm` works the grid out
 * from them - the same code `panel {}` is laid out by, so the column count, the shape of a row with a
 * label, the space between two rows and where a comment goes are not merely alike but the same, and the
 * measurement is the same grid's as well.
 *
 * A form nested in a row of another form is an island: it is measured as a form, but the form around it
 * cannot see inside it, so nothing lines up across the boundary. [FormScope.FormGroup] and
 * [FormScope.FormIndent] are what group and step rows in *this* form.
 *
 * @see com.intellij.ui.dsl.builder.panel
 */
@Composable
@ApiStatus.Experimental
public fun FormPanel(
  modifier: SwingModifier = SwingModifier,
  content: @Composable FormScope.() -> Unit,
) {
  val scope = remember { FormScopeInstance(group = null, indentLevel = 0) }
  SwingNode(
    factory = { JPanel(newFormLayout()) },
    update = { applyModifier(modifier) },
    content = { scope.content() },
  )
}

/**
 * What a [FormPanel] holds.
 *
 * These are composables rather than declarations a scope records, so a row is identified by where it is
 * written, the way everything else in a composition is: a row that appears above another leaves the rows
 * below it holding the components they already held, and a list of rows is reordered with
 * [androidx.compose.runtime.key] as any other list is. A form composes them into its own panel, so a
 * component emitted into a form rather than into one of its rows has nowhere to go and is reported when the
 * form is laid out.
 *
 * @see com.intellij.ui.dsl.builder.Panel
 */
@ApiStatus.Experimental
public sealed interface FormScope {
  /**
   * A row of the form: an optional [label], the controls [content] emits, and an optional [comment] under
   * them.
   *
   * A row with a [label] lines that label up with the labels of the rows around it, and holds everything
   * after the label in a grid of its own - so a row holding three controls does not make three columns of
   * every other row. A row without a label lays its controls out independently of the other rows.
   *
   * Each control [content] emits takes a column of the row, in the order they are emitted; how a control
   * looks is its own `modifier`'s business, and [FormRowScope.cell] is what a control says about the cell
   * it is given. [resizable] gives the row the vertical space the form has left over, which is what a row
   * holding a list or a log wants.
   *
   * [topGap] and [bottomGap] set the row apart from its neighbours, for a row that starts a block of its
   * own without a group's separator to announce it. Two neighbours that both ask are one gap apart, not
   * two, and the space a row keeps anyway - under a comment, around a group - is what it keeps when
   * neither is given.
   *
   * @see com.intellij.ui.dsl.builder.Panel.row
   * @see com.intellij.ui.dsl.builder.Row.rowComment
   * @see com.intellij.ui.dsl.builder.Row.resizableRow
   */
  @Composable
  public fun FormRow(
    label: @NlsContexts.Label String? = null,
    comment: @NlsContexts.DetailedDescription String? = null,
    resizable: Boolean = false,
    topGap: FormGap? = null,
    bottomGap: FormGap? = null,
    content: @Composable FormRowScope.() -> Unit,
  )

  /**
   * A titled group: a separator carrying [title], then [content], indented unless [indent] is false. A
   * group has columns of its own, so its rows line up against each other rather than against the rows
   * outside it, and it keeps a step away from what surrounds it.
   *
   * @see com.intellij.ui.dsl.builder.Panel.group
   */
  @Composable
  public fun FormGroup(
    title: @NlsContexts.Separator String? = null,
    indent: Boolean = true,
    content: @Composable FormScope.() -> Unit,
  )

  /**
   * Rows indented one level further than the rows around them. They stay rows of this form, so their
   * labels and columns still line up with everything else in it.
   *
   * @see com.intellij.ui.dsl.builder.Panel.indent
   */
  @Composable
  public fun FormIndent(content: @Composable FormScope.() -> Unit)

  /**
   * A separator spanning the width of the form.
   *
   * @see com.intellij.ui.dsl.builder.Panel.separator
   */
  @Composable
  public fun FormSeparator()

  /**
   * A row holding nothing but [text] as a comment: smaller, dimmed, wrapped to the width of the form.
   *
   * @see com.intellij.ui.dsl.builder.Row.comment
   */
  @Composable
  public fun FormComment(text: @NlsContexts.DetailedDescription String)
}

/**
 * How much space a row keeps from the row beside it, in the steps the platform's forms are spaced by.
 *
 * The measurements are [com.intellij.ui.dsl.builder.SpacingConfiguration]'s, so a gap here is the same
 * distance as the same gap in a `panel {}` form, on every theme and at every scale.
 *
 * @see com.intellij.ui.dsl.builder.TopGap
 * @see com.intellij.ui.dsl.builder.BottomGap
 */
@ApiStatus.Experimental
public enum class FormGap {
  /** No space, in place of the space the row would otherwise keep. */
  NONE,

  /** [com.intellij.ui.dsl.builder.SpacingConfiguration.verticalSmallGap]. */
  SMALL,

  /** [com.intellij.ui.dsl.builder.SpacingConfiguration.verticalMediumGap]. */
  MEDIUM,
}

/**
 * What a [FormScope.FormRow] holds: the controls of that row, emitted in the order they take its columns.
 *
 * A control that wants nothing of its cell is emitted plainly; one that does says so with [cell] on its own
 * modifier.
 *
 * @see com.intellij.ui.dsl.builder.Row
 */
@ApiStatus.Experimental
public sealed interface FormRowScope {
  /**
   * What this control asks of the cell it is given.
   *
   * [fillWidth] gives it the width of the cell rather than the width it asks for - a text field that should
   * reach the edge of the page, a scroll pane, a table. A comment or a label that wraps to the width it is
   * given already fills without being asked.
   *
   * [smallGapAfter] leaves only a small gap to the control after it, rather than the gap that separates two
   * unrelated ones - an icon standing before the text it belongs to. The gap is
   * [com.intellij.ui.dsl.builder.SpacingConfiguration.horizontalSmallGap], and a control at the end of its
   * row keeps no gap either way.
   *
   * A control declares its cell once: a chain carrying two of these keeps the last, as any two placements
   * on one chain do.
   *
   * @see com.intellij.ui.dsl.builder.AlignX.FILL
   * @see com.intellij.ui.dsl.builder.RightGap.SMALL
   */
  public fun SwingModifier.cell(
    fillWidth: Boolean = false,
    smallGapAfter: Boolean = false,
  ): SwingModifier
}

/**
 * The layout of a form's panel: the platform grid, built from the children by [FormGridLayout].
 *
 * `respectMinimumSize` is what `panel {}` sets as well, and without it a form reports its preferred size as
 * its minimum one and will not give up the space it is not using when it shares a split view.
 */
private fun newFormLayout(): GridLayout =
  FormGridLayout().apply { respectMinimumSize = true }

// --- What a component says about the row it is in -----------------------------------------------

/**
 * The identity of a group, for as long as it is declared, and where it stands in the form around it.
 * [parent] is the group it is nested in, or `null` for a group of the form itself.
 *
 * A group has no component of its own - it is a grid - so the form learns of it only from the rows inside
 * it.
 */
internal class FormGroupToken(val parent: FormGroupToken?, val indent: Int)

/**
 * What a component the form itself emits says about the row it opens or closes. It travels as the
 * component's layout constraint, so the form reads its structure back off the panel and the grid is
 * rebuilt whenever any of it changes.
 *
 * A control the caller emits carries a [FormCellMark] or nothing at all, and belongs to whichever row is
 * open where it stands - which is what makes the panel's own child order, the order the components were
 * composed in, the whole of what the form is read from.
 */
internal sealed interface FormMark

/**
 * Opens a row. The component carrying it is the row's label when [labeled], and otherwise a
 * [FormRowBoundary] that marks where the row begins.
 */
internal data class FormRowMark(
  val group: FormGroupToken?,
  val labeled: Boolean,
  val indent: Int,
  val resizable: Boolean,
  val topGap: FormGap?,
  val bottomGap: FormGap?,
) : FormMark

/** The comment of the row that is open, which goes under what it holds rather than under its label. */
internal data object FormCommentMark : FormMark

/**
 * Closes the row that is open. Every row ends with one, so a component standing between two rows belongs to
 * neither and is reported rather than taken for a control of the row above it.
 */
internal data object FormRowEndMark : FormMark

/** What one control asks of its cell. */
internal data class FormCellMark(
  val fillWidth: Boolean,
  val smallGapAfter: Boolean,
) : FormMark

private class FormScopeInstance(
  private val group: FormGroupToken?,
  private val indentLevel: Int,
) : FormScope {
  @Composable
  override fun FormRow(
    label: @NlsContexts.Label String?,
    comment: @NlsContexts.DetailedDescription String?,
    resizable: Boolean,
    topGap: FormGap?,
    bottomGap: FormGap?,
    content: @Composable FormRowScope.() -> Unit,
  ) {
    val mark = FormRowMark(group, label != null, indentLevel, resizable, topGap, bottomGap)

    FormRowStart(label, SwingModifier.layoutConstraint(mark))
    FormRowScopeInstance.content()
    if (comment != null) {
      Comment(
        comment,
        maxLineLength = DEFAULT_COMMENT_WIDTH,
        modifier = SwingModifier.layoutConstraint(FormCommentMark),
      )
    }
    FormRowBoundary(SwingModifier.layoutConstraint(FormRowEndMark))
  }

  @Composable
  override fun FormGroup(
    title: @NlsContexts.Separator String?,
    indent: Boolean,
    content: @Composable FormScope.() -> Unit,
  ) {
    val nested = remember(group, indentLevel) { FormGroupToken(group, indentLevel) }
    // A group starts its own indenting, so its title stands at the group's left edge and what the group
    // holds steps in from there rather than from the form's edge.
    val scope = remember(nested) { FormScopeInstance(nested, indentLevel = 0) }

    with(scope) {
      if (title != null) {
        FormRow { FormTitledSeparator(title, SwingModifier.cell(fillWidth = true)) }
      }
      if (indent) FormIndent(content) else content()
    }
  }

  @Composable
  override fun FormIndent(content: @Composable FormScope.() -> Unit) {
    val scope = remember(group, indentLevel) { FormScopeInstance(group, indentLevel + 1) }
    scope.content()
  }

  @Composable
  override fun FormSeparator() {
    FormRow { FormSeparatorComponent(SwingModifier.cell(fillWidth = true)) }
  }

  @Composable
  override fun FormComment(text: @NlsContexts.DetailedDescription String) {
    FormRow {
      Comment(text, maxLineLength = MAX_LINE_LENGTH_WORD_WRAP, modifier = SwingModifier.cell(fillWidth = true))
    }
  }
}

/**
 * The row scope holds nothing: a cell reaches the grid on the control's own chain, so one instance serves
 * every row of every form.
 */
private object FormRowScopeInstance : FormRowScope {
  override fun SwingModifier.cell(fillWidth: Boolean, smallGapAfter: Boolean): SwingModifier =
    layoutConstraint(FormCellMark(fillWidth, smallGapAfter))
}

// --- The components a form supplies itself ------------------------------------------------------

/**
 * The component that opens a row: its label, or - for a row that has no label to mark it - a
 * [FormRowBoundary].
 */
@Composable
private fun FormRowStart(text: @NlsContexts.Label String?, modifier: SwingModifier) {
  if (text == null) {
    FormRowBoundary(modifier)
    return
  }
  SwingNode(
    factory = {
      JLabel().apply {
        // What marks a component as the label of its row, and so what the narrower space between a label
        // and the thing it labels is chosen by.
        putClientProperty(DslComponentProperty.ROW_LABEL, true)
      }
    },
    // A label spells a mnemonic the way every other IDE label does, with an ampersand before the letter.
    update = {
      set(text) { this.text = BundleBase.replaceMnemonicAmpersand(it) }
      applyModifier(modifier)
    },
  )
}

/**
 * Where a row begins or ends. The form is read off its panel's children in the order they were composed in,
 * and a row is the span between its boundaries; a boundary is given no cell, so it has no size, paints
 * nothing and takes no focus.
 */
@Composable
private fun FormRowBoundary(modifier: SwingModifier) {
  SwingNode(factory = { FormRowBoundaryComponent() }, update = { applyModifier(modifier) })
}

internal class FormRowBoundaryComponent : JComponent()

@Composable
private fun FormSeparatorComponent(modifier: SwingModifier) {
  SwingNode(
    factory = { SeparatorComponent(0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), null) },
    update = { applyModifier(modifier) },
  )
}

@Composable
private fun FormTitledSeparator(title: @NlsContexts.Separator String, modifier: SwingModifier) {
  // A titled separator draws space of its own above and below, and a form decides that space.
  SwingNode(
    factory = { IdeaTitledSeparator().apply { border = null } },
    update = {
      set(title) { this.text = it }
      applyModifier(modifier)
    },
  )
}
