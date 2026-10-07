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
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.componentDefaultKeyOf
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
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
 *     FormRow("Log:", resizable = true) { LogView(SwingModifier.align(FormAlignX.FILL)) }
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
 * | `cell(c).align(AlignX.FILL)` | `C(SwingModifier.align(FormAlignX.FILL))` |
 * | `cell(c).gap(RightGap.SMALL)` | `C(SwingModifier.gap(FormRightGap.SMALL))` |
 * | `cell(c).label("Name:", LabelPosition.TOP)` | `FormRow("Name:", labelPosition = FormLabelPosition.TOP)` |
 * | `.widthGroup("g")` | `SwingModifier.widthGroup("g")` |
 * | `.resizableColumn()` | `SwingModifier.resizableColumn()` |
 * | `.comment("...", maxLineLength)` | `FormRow(comment = "...", commentMaxLineLength = n)` |
 * | `.comment("...")`, `.rowComment("...")` | `FormRow(comment = "...")` |
 * | `.resizableRow()` | `FormRow(resizable = true)` |
 * | `.topGap(TopGap.SMALL)`, `.bottomGap(...)` | `FormRow(topGap = FormGap.SMALL)`, `bottomGap = ...` |
 * | `group("Title") { }`, `indent { }`, `separator()` | `FormGroup`, `FormIndent`, `FormSeparator` |
 * | `row { comment("...") }` | `FormComment("...")` |
 * | `.bindText(::property)`, `.bindSelected(...)` | state in, callback out; see `SettingState` |
 * | `.visibleIf(predicate)`, `.enabledIf(...)` | an `if` around the row, or a `SwingModifier` |
 *
 * A cell is left-aligned and vertically centered unless [FormRowScope.align] says otherwise.
 * Exact gaps stay with `panel {}`.
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
    modifier = modifier,
    // A form nested in a row of another form holds rows of its own, not the outer row's controls.
    content = { ProvideComponentDefaults(FormRowDefault provides null) { scope.content() } },
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
@LayoutScopeMarker
public sealed interface FormScope {
  /**
   * A row of the form: an optional [label], the controls [content] emits, and an optional [comment] under
   * them.
   * [labelPosition] puts the label on the left by default, or above the first control.
   * [commentMaxLineLength] sets the comment's wrap width in characters.
   *
   * [label] is given to the first control, when that control can take it, as `row("Label:") { }` gives it.
   * [comment] goes under the first control and is its accessible description, as `cell(c).comment(...)` is for
   * the control it is declared on.
   *
   * A row with a [label] on the left lines that label up with the labels of the rows around it, and holds
   * everything after the label in a grid of its own - so a row holding three controls does not make three
   * columns of every other row. A row without a label, or with its label above, lays its controls out
   * independently of the other rows.
   *
   * Each control [content] emits takes a column of the row, in the order they are emitted; how a control
   * looks is its own `modifier`'s business, and the modifiers of [FormRowScope] are what a control says about the
   * cell it is given. [resizable] gives the row the vertical space the form has left over, which is what a row
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
    labelPosition: FormLabelPosition = FormLabelPosition.LEFT,
    commentMaxLineLength: Int = DEFAULT_COMMENT_WIDTH,
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
 * A control that wants nothing of its cell is emitted plainly. One that does says so with the modifiers of
 * this scope on its own `modifier`. The modifiers combine on one chain, and a repeated one replaces the
 * earlier one of its kind.
 *
 * @see com.intellij.ui.dsl.builder.Row
 */
@ApiStatus.Experimental
@LayoutScopeMarker
public sealed interface FormRowScope {
  /**
   * Places this control in its cell along one axis.
   *
   * [FormAlignX.FILL] gives the control the width of the cell rather than the width it asks for. Use it for
   * a text field that reaches the edge of the page, a scroll pane, or a table.
   * [FormAlignY.FILL] gives it the height of the cell in the same way.
   * A control stands at [FormAlignX.LEFT] and [FormAlignY.CENTER] by default.
   * Call it once with a [FormAlignX] and once with a [FormAlignY] to set both axes.
   *
   * @see com.intellij.ui.dsl.builder.Cell.align
   */
  public fun SwingModifier.align(align: FormAlign): SwingModifier

  /**
   * Sets the gap between this control and the one after it.
   *
   * [FormRightGap.SMALL] fits an icon that stands before the text it belongs to.
   * [FormRightGap.COLUMNS] separates two columns.
   * A control at the end of its row keeps no gap either way.
   *
   * @see com.intellij.ui.dsl.builder.Cell.gap
   */
  public fun SwingModifier.gap(rightGap: FormRightGap): SwingModifier

  /**
   * Gives the controls in the group named [group] the same width.
   * A control cannot be in a width group and also use [FormAlignX.FILL].
   * The form reports that conflict when it lays the control out.
   *
   * @see com.intellij.ui.dsl.builder.Cell.widthGroup
   */
  public fun SwingModifier.widthGroup(group: String): SwingModifier

  /**
   * Lets the column of this control take spare horizontal space.
   *
   * @see com.intellij.ui.dsl.builder.Cell.resizableColumn
   */
  public fun SwingModifier.resizableColumn(): SwingModifier
}

/**
 * Where a control stands in its cell along one axis.
 * A control is placed by [FormAlignX] across and [FormAlignY] along the height.
 *
 * @see com.intellij.ui.dsl.builder.Align
 */
@ApiStatus.Experimental
public sealed interface FormAlign

/**
 * The horizontal place of a control in its cell.
 *
 * @see com.intellij.ui.dsl.builder.AlignX
 */
@ApiStatus.Experimental
public enum class FormAlignX : FormAlign {
  /** [com.intellij.ui.dsl.builder.AlignX.LEFT]: the control stands at the left edge of the cell. */
  LEFT,

  /** [com.intellij.ui.dsl.builder.AlignX.CENTER]: the control stands in the middle of the cell. */
  CENTER,

  /** [com.intellij.ui.dsl.builder.AlignX.RIGHT]: the control stands at the right edge of the cell. */
  RIGHT,

  /** [com.intellij.ui.dsl.builder.AlignX.FILL]: the control takes the full width of the cell. */
  FILL,
}

/**
 * The vertical place of a control in its cell.
 *
 * @see com.intellij.ui.dsl.builder.AlignY
 */
@ApiStatus.Experimental
public enum class FormAlignY : FormAlign {
  /** [com.intellij.ui.dsl.builder.AlignY.TOP]: the control stands at the top of the cell. */
  TOP,

  /** [com.intellij.ui.dsl.builder.AlignY.CENTER]: the control stands in the middle of the cell. */
  CENTER,

  /** [com.intellij.ui.dsl.builder.AlignY.BOTTOM]: the control stands at the bottom of the cell. */
  BOTTOM,

  /** [com.intellij.ui.dsl.builder.AlignY.FILL]: the control takes the full height of the cell. */
  FILL,
}

/**
 * The gap after a control, in the steps the platform's forms use.
 *
 * @see com.intellij.ui.dsl.builder.RightGap
 */
@ApiStatus.Experimental
public enum class FormRightGap {
  /** [com.intellij.ui.dsl.builder.RightGap.SMALL]: the control belongs to the one after it. */
  SMALL,

  /** [com.intellij.ui.dsl.builder.RightGap.COLUMNS]: the gap that separates two columns. */
  COLUMNS,
}

/**
 * Where the label of a [FormScope.FormRow] stands.
 *
 * @see com.intellij.ui.dsl.builder.LabelPosition
 */
@ApiStatus.Experimental
public enum class FormLabelPosition {
  /** The label stands left of the controls and lines up with the labels of the rows around it. */
  LEFT,

  /** The label stands above the first control. */
  TOP,
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

/** The identity of a row, for as long as its [FormScope.FormRow] is composed. */
internal class FormRowToken

/**
 * What a component says about the place it takes in its row. It travels as the component's parent data, so the
 * form reads its structure back off the panel and the grid is rebuilt whenever any of it changes.
 */
internal sealed interface FormMark

/**
 * A row, as every component composed in it carries it: [token] tells it from its neighbours, and the rest is
 * what [FormScope.FormRow] was declared with. [comment] and [commentMaxLineLength] are here so that a change of
 * either rebuilds the grid, which describes the row's first control with the comment and chooses its cell by
 * how it wraps. The component carrying it as its own mark is the row's label.
 */
internal data class FormRowMark(
  val token: FormRowToken,
  val group: FormGroupToken?,
  val indent: Int,
  val resizable: Boolean,
  val topGap: FormGap?,
  val bottomGap: FormGap?,
  val comment: String?,
  val labelPosition: FormLabelPosition,
  val commentMaxLineLength: Int,
) : FormMark

/** The comment of [row], which goes under what it holds rather than under its label. */
internal data class FormCommentMark(val row: FormRowMark) : FormMark

/**
 * What one control asks of its cell. Every value compares by what it says, so recomposing a control with the
 * same options makes an equal mark and leaves the grid as it is.
 */
internal data class FormCellMark(
  val widthGroup: String? = null,
  val resizableColumn: Boolean = false,
  val alignX: FormAlignX? = null,
  val alignY: FormAlignY = FormAlignY.CENTER,
  val rightGap: FormRightGap? = null,
) : FormMark

/**
 * The row a control is composed in. [FormScope.FormRow] provides it to every component in its content as a
 * component default, so a control needs no declaration of its own to take a cell of the row.
 */
internal data class FormRowElement(val row: FormRowMark) : ParentLayoutElement {
  override val parentProtocol: ParentProtocol get() = FormParentProtocol

  override val inheritable: Boolean get() = true

  override val name: String get() = "formRow"

  override val declaredValues: Map<String, Any?> get() = mapOf("row" to row)
}

private val FormRowDefault = componentDefaultKeyOf<FormRowMark>("FormRow") { this then FormRowElement(it) }

/** The parents a [FormMark] is declared for: the panel of a [FormPanel], and no other. */
private val FormParentProtocol: ParentProtocol = parentProtocolOf("FormPanel row") { it.layout is FormGridLayout }

/** Declares [mark] to the form the component stands in. A later mark on the same chain replaces an earlier one. */
private fun SwingModifier.formMark(mark: FormMark): SwingModifier = this then FormMarkElement(mark)

private data class FormMarkElement(private val mark: FormMark) : ParentDataModifier {
  override val parentProtocol: ParentProtocol get() = FormParentProtocol

  override val name: String get() = "formMark"

  override val declaredValues: Map<String, Any?> get() = mapOf("mark" to mark)

  override fun modifyParentData(parentData: Any?): Any = mark
}

/**
 * A cell option of [FormRowScope]. Each kind folds its one value into the [FormCellMark] the chain carries so far,
 * so options of different kinds combine, and a repeated kind keeps its last value.
 */
private sealed class FormCellElement : ParentDataModifier {
  final override val parentProtocol: ParentProtocol get() = FormParentProtocol

  override val key: Any get() = this::class

  final override fun modifyParentData(parentData: Any?): Any = fold(parentData as? FormCellMark ?: FormCellMark())

  protected abstract fun fold(mark: FormCellMark): FormCellMark
}

private data class FormAlignElement(private val align: FormAlign) : FormCellElement() {
  // One key per axis, so an alignment on each axis combines on one chain.
  override val key: Any
    get() = when (align) {
      is FormAlignX -> FormAlignX::class
      is FormAlignY -> FormAlignY::class
    }

  override val name: String get() = "formAlign"

  override val declaredValues: Map<String, Any?> get() = mapOf("align" to align)

  override fun fold(mark: FormCellMark): FormCellMark =
    when (align) {
      is FormAlignX -> mark.copy(alignX = align)
      is FormAlignY -> mark.copy(alignY = align)
    }
}

private data class FormGapElement(private val rightGap: FormRightGap) : FormCellElement() {
  override val name: String get() = "formGap"

  override val declaredValues: Map<String, Any?> get() = mapOf("rightGap" to rightGap)

  override fun fold(mark: FormCellMark): FormCellMark = mark.copy(rightGap = rightGap)
}

private data class FormWidthGroupElement(private val group: String) : FormCellElement() {
  override val name: String get() = "formWidthGroup"

  override val declaredValues: Map<String, Any?> get() = mapOf("group" to group)

  override fun fold(mark: FormCellMark): FormCellMark = mark.copy(widthGroup = group)
}

private data object FormResizableColumnElement : FormCellElement() {
  override val name: String get() = "formResizableColumn"

  override val declaredValues: Map<String, Any?> get() = emptyMap()

  override fun fold(mark: FormCellMark): FormCellMark = mark.copy(resizableColumn = true)
}

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
    labelPosition: FormLabelPosition,
    commentMaxLineLength: Int,
    content: @Composable FormRowScope.() -> Unit,
  ) {
    val token = remember { FormRowToken() }
    val row = FormRowMark(token, group, indentLevel, resizable, topGap, bottomGap, comment, labelPosition, commentMaxLineLength)

    if (label != null) {
      FormRowStart(label, SwingModifier.formMark(row))
    }
    ProvideComponentDefaults(FormRowDefault provides row) {
      FormRowScopeInstance.content()
    }
    if (comment != null) {
      Comment(
        comment,
        modifier = SwingModifier.formMark(FormCommentMark(row)),
        maxLineLength = commentMaxLineLength,
      )
    }
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
        FormRow { FormTitledSeparator(title, SwingModifier.align(FormAlignX.FILL)) }
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
    FormRow { FormSeparatorComponent(SwingModifier.align(FormAlignX.FILL)) }
  }

  @Composable
  override fun FormComment(text: @NlsContexts.DetailedDescription String) {
    FormRow {
      Comment(text, modifier = SwingModifier.align(FormAlignX.FILL), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
    }
  }
}

/**
 * The row scope holds nothing: a cell reaches the grid on the control's own chain, so one instance serves
 * every row of every form.
 */
private object FormRowScopeInstance : FormRowScope {
  override fun SwingModifier.align(align: FormAlign): SwingModifier = this then FormAlignElement(align)

  override fun SwingModifier.gap(rightGap: FormRightGap): SwingModifier = this then FormGapElement(rightGap)

  override fun SwingModifier.widthGroup(group: String): SwingModifier = this then FormWidthGroupElement(group)

  override fun SwingModifier.resizableColumn(): SwingModifier = this then FormResizableColumnElement
}

// --- The components a form supplies itself ------------------------------------------------------

/** The component that opens a row: its label. */
@Composable
private fun FormRowStart(text: @NlsContexts.Label String, modifier: SwingModifier) {
  SwingNode(
    factory = {
      JLabel().apply {
        // What marks a component as the label of its row, and so what the narrower space between a label
        // and the thing it labels is chosen by.
        putClientProperty(DslComponentProperty.ROW_LABEL, true)
      }
    },
    modifier = modifier,
    // A label spells a mnemonic the way every other IDE label does, with an ampersand before the letter.
    update = {
      set(text) { this.text = BundleBase.replaceMnemonicAmpersand(it) }
    },
  )
}

@Composable
private fun FormSeparatorComponent(modifier: SwingModifier) {
  SwingNode(
    factory = { SeparatorComponent(0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), null) },
    modifier = modifier,
  )
}

@Composable
private fun FormTitledSeparator(title: @NlsContexts.Separator String, modifier: SwingModifier) {
  // A titled separator draws space of its own above and below, and a form decides that space.
  SwingNode(
    factory = { IdeaTitledSeparator().apply { border = null } },
    modifier = modifier,
    update = {
      set(title) { this.text = it }
    },
  )
}
