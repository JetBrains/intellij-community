// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.impl.GridFormComponentCell
import com.intellij.ui.dsl.builder.impl.GridFormPanelCell
import com.intellij.ui.dsl.builder.impl.GridFormRow
import com.intellij.ui.dsl.builder.impl.buildGridForm
import com.intellij.ui.dsl.builder.impl.checkJComponent
import com.intellij.ui.dsl.builder.impl.errorInInternalOrLogWarn
import com.intellij.ui.dsl.builder.impl.labelComponent
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.BadLocationException

/**
 * The layout of a [FormPanel]: a platform grid worked out from what the panel's children say they are.
 *
 * A component cannot be given a cell as it arrives, because where it belongs depends on the whole form: the
 * column count is the widest row's, a group has columns of its own, and the space between two rows is the
 * larger of what the two ask for. So the components arrive in the order they were declared in, each saying
 * what it is - see [FormMark] - and the grid is built from all of them together, as late as the first
 * measurement and again whenever they change.
 *
 * Where the cells then go is not decided here: the form is handed to [buildGridForm], the same code
 * `panel { }` is laid out by, so a page written in Compose is laid out by the rules every other settings page
 * is laid out by rather than by rules that resemble them.
 *
 * The components are untouched by a rebuild - they are never taken out of the panel and never recreated - so
 * what they hold, and the focus and the selection in them, survives a row appearing or disappearing above.
 */
internal class FormGridLayout : GridLayout(), MeasurementLayoutManager {

  /** What each component was added with, and whether the grid has yet to be built from them. */
  private val marks = IdentityHashMap<JComponent, FormChildMarks>()
  private var gridOutOfDate = false

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    // The grid registers its own cells through this method, and those carry a Constraints, so what tells the
    // two apart is the constraints themselves rather than who is adding the component.
    if (constraints is Constraints) {
      super.addLayoutComponent(comp, constraints)
    }
  }

  override fun declareComponentLayout(component: Component, parentData: Any?, elements: List<ParentLayoutElement>) {
    val mark = parentData as? FormMark
    val row = elements.firstNotNullOfOrNull { (it as? FormRowElement)?.row }
      ?: when (mark) {
        is FormRowMark -> mark
        is FormCommentMark -> mark.row
        else -> null
      }
    // Updated in place: the entry lives as long as its component, as a CellImpl lives as long as its cell.
    marks.getOrPut(checkJComponent(component)) { FormChildMarks() }.also {
      it.row = row
      it.mark = mark
    }
    gridOutOfDate = true
  }

  override fun removeLayoutComponent(comp: Component?) {
    // The cells the component held go when the grid is built again, which is what leaving them behind here
    // asks for. A component that was never given a cell is nothing to complain about either: it was accepted
    // without one in the first place.
    marks.remove(checkJComponent(comp))
    gridOutOfDate = true
  }

  override fun preferredLayoutSize(parent: Container?): Dimension {
    buildGrid(checkNotNull(parent))
    return super.preferredLayoutSize(parent)
  }

  override fun minimumLayoutSize(parent: Container?): Dimension {
    buildGrid(checkNotNull(parent))
    return super.minimumLayoutSize(parent)
  }

  override fun layoutContainer(parent: Container?) {
    buildGrid(checkNotNull(parent) { "Parent is null" })
    super.layoutContainer(parent)
  }

  override fun getConstraints(component: JComponent): Constraints? {
    // A component has a cell as soon as it is in the container this lays out, whether or not anything has
    // measured that container yet.
    val parent = component.parent
    if (parent != null && parent.layout === this) {
      buildGrid(parent)
    }
    return super.getConstraints(component)
  }

  /**
   * Builds the grid of [parent] from its components, if they have changed since it was last built. The grid is
   * replaced rather than emptied, because a grid holds every cell it is given for as long as it lives.
   */
  private fun buildGrid(parent: Container) {
    synchronized(parent.treeLock) {
      if (!gridOutOfDate) {
        return
      }

      // The spacing is read afresh here because two of its values are derived from the look and feel's
      // checkbox metrics, so a form built after a theme change is built to the new ones.
      val spacing = IntelliJSpacingConfiguration()
      // Read before the grid is reset, so a declaration that cannot be laid out leaves the grid out of date.
      val form = toGridForm(readItems(parent), spacing)

      // Cleared first: the cells are registered through addLayoutComponent, which would otherwise take them
      // for components still to be placed and ask for the grid to be built again.
      gridOutOfDate = false
      resetRootGrid()
      buildGridForm(form, RowsGridBuilder(checkJComponent(parent)), spacing)
    }
  }

  private fun toGridForm(items: List<FormItem>, spacing: SpacingConfiguration): List<GridFormRow> =
    items.map { item ->
      when (item) {
        is FormRowContent -> toRow(item, spacing)
        is FormGroupContent -> toGroup(item, spacing)
      }
    }

  /**
   * A row with a label lines up with the rows around it, so its label goes in the form's own grid and
   * everything else in a grid of its own; a row without one lines up with nothing and is a grid of its own
   * entirely. A comment belongs to what the row holds rather than to the row, and so is given to the first
   * thing it holds - which is what puts it under the controls rather than under the label.
   */
  private fun toRow(row: FormRowContent, spacing: SpacingConfiguration): GridFormRow {
    val comment = row.comment
    // As `cell(c).comment(...)` does, the comment describes the control it stands under.
    row.cells.firstOrNull()?.component?.let { marks.getValue(it).updateAccessibleContextDescription(it, comment) }
    row.label?.let { label ->
      label.labelFor = null
      row.cells.firstOrNull()?.let { labelComponent(label, it.component) }
    }
    val controls = row.cells.mapIndexed { index, cell ->
      check(cell.mark.widthGroup == null || cell.mark.alignX != FormAlignX.FILL) {
        "The control ${cell.component} is in the width group '${cell.mark.widthGroup}' and fills the width. " +
        "Drop widthGroup or align(FormAlignX.FILL)."
      }
      // As CellImpl.align does, an alignment that is declared decides whether a wrapping comment is held to
      // its width; one that is not declared leaves the value Comment gave it.
      (cell.component as? DslLabel)?.takeIf { it.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP }?.let {
        it.limitPreferredSize = limitsPreferredSize(it.maxLineLength, cell.mark.alignX)
      }
      GridFormComponentCell(
        component = cell.component,
        comment = if (index == 0) comment else null,
        label = if (index == 0 && row.labelPosition == FormLabelPosition.TOP) row.label else null,
        labelPosition = when (row.labelPosition) {
          FormLabelPosition.LEFT -> LabelPosition.LEFT
          FormLabelPosition.TOP -> LabelPosition.TOP
        },
        widthGroup = cell.mark.widthGroup,
        horizontalAlign = when (cell.mark.alignX) {
          null, FormAlignX.LEFT -> HorizontalAlign.LEFT
          FormAlignX.CENTER -> HorizontalAlign.CENTER
          FormAlignX.RIGHT -> HorizontalAlign.RIGHT
          FormAlignX.FILL -> HorizontalAlign.FILL
        },
        verticalAlign = when (cell.mark.alignY) {
          FormAlignY.TOP -> VerticalAlign.TOP
          FormAlignY.CENTER -> VerticalAlign.CENTER
          FormAlignY.BOTTOM -> VerticalAlign.BOTTOM
          FormAlignY.FILL -> VerticalAlign.FILL
        },
        resizableColumn = cell.mark.resizableColumn,
        rightGap = when (cell.mark.rightGap) {
          null -> null
          FormRightGap.SMALL -> RightGap.SMALL
          FormRightGap.COLUMNS -> RightGap.COLUMNS
        },
        customGaps = null,
      )
    }
    val topLabel = row.label.takeIf { row.labelPosition == FormLabelPosition.TOP }
    val label = row.label.takeIf { row.labelPosition == FormLabelPosition.LEFT }
      ?: return GridFormRow(
        rowLayout = RowLayout.INDEPENDENT,
        // A row holding nothing but its comment has nothing to put the comment under, so the comment is
        // what the row holds - the row FormComment writes. A top label with no control to stand over is what
        // the row holds in the same way, with the comment under it.
        cells = when {
          controls.isNotEmpty() -> controls
          topLabel != null -> listOf(GridFormComponentCell(component = topLabel, comment = comment))
          comment != null -> listOf(toCommentCell(comment))
          else -> controls
        },
        indent = row.indent * spacing.horizontalIndent,
        resizableRow = row.resizable,
        topGap = row.topGap?.toTopGap(),
        bottomGap = row.bottomGap?.toBottomGap(),
      )

    // A row that holds nothing but a label still has somewhere to put its comment.
    val labelCell = GridFormComponentCell(component = label, comment = if (controls.isEmpty()) comment else null)
    return GridFormRow(
      rowLayout = RowLayout.LABEL_ALIGNED,
      cells = listOf(labelCell) + controls,
      indent = row.indent * spacing.horizontalIndent,
      resizableRow = row.resizable,
      topGap = row.topGap?.toTopGap(),
      bottomGap = row.bottomGap?.toBottomGap(),
    )
  }

  private fun FormGap.toTopGap(): TopGap =
    when (this) {
      FormGap.NONE -> TopGap.NONE
      FormGap.SMALL -> TopGap.SMALL
      FormGap.MEDIUM -> TopGap.MEDIUM
    }

  private fun FormGap.toBottomGap(): BottomGap =
    when (this) {
      FormGap.NONE -> BottomGap.NONE
      FormGap.SMALL -> BottomGap.SMALL
      FormGap.MEDIUM -> BottomGap.MEDIUM
    }

  /** A comment standing on its own takes the width of the form when it is the form that wraps it. */
  private fun toCommentCell(comment: DslLabel): GridFormComponentCell =
    GridFormComponentCell(
      component = comment,
      horizontalAlign = if (comment.wrapsToTheWidthItIsGiven()) HorizontalAlign.FILL else HorizontalAlign.LEFT,
    )

  /**
   * Whether a component lays its text out to the width it is given rather than asking for a width of its own.
   *
   * A comment on a row of its own is given the width that is left, because there is nothing else on the row to
   * give it to. A comment *beside* a control is not, even though `panel {}` fills one
   * (`RowImpl.comment`, `RowImpl.text`): a row here holds only the controls it was written with, where the row
   * it replaces holds one per state and hides all but the applicable one, so the same comment that fills a
   * middle column there would fill to the end of the row here - and a row whose last cell claims the rest of
   * the width stands 3px narrower and shorter than its neighbours. A row that wants it says
   * [FormRowScope.align] with [FormAlignX.FILL].
   */
  private fun JComponent.wrapsToTheWidthItIsGiven(): Boolean =
    this is DslLabel && maxLineLength == MAX_LINE_LENGTH_WORD_WRAP

  /**
   * A group takes a row of the form and holds a form of its own inside it, so its columns are settled within
   * it and move nothing outside. It keeps a step away from what surrounds it, which is taken once between two
   * neighbours that both ask for it.
   */
  private fun toGroup(group: FormGroupContent, spacing: SpacingConfiguration): GridFormRow =
    GridFormRow(
      rowLayout = RowLayout.INDEPENDENT,
      cells = listOf(
        GridFormPanelCell(
          rows = toGridForm(group.items, spacing),
          spacingConfiguration = spacing,
          verticalAlign = VerticalAlign.FILL,
        )
      ),
      indent = group.indent * spacing.horizontalIndent,
      internalGaps = UnscaledGapsY(top = spacing.verticalMediumGap, bottom = spacing.verticalMediumGap),
    )

  /**
   * Reads the form's declared structure off the children of [parent], in the order they were composed in.
   *
   * Every component of a form knows its row: the row's label and comment from the [FormMark] the form gives
   * them, and a control from the [FormRowElement] its row provides to it. A row's components stand together,
   * so the rows come out in the order their first components do.
   *
   * A component standing outside every row belongs nowhere, and is reported rather than shown in a place it
   * was not declared in.
   */
  private fun readItems(parent: Container): List<FormItem> {
    val root = mutableListOf<FormItem>()
    val groups = IdentityHashMap<FormGroupToken, FormGroupContent>()
    val rows = IdentityHashMap<FormRowToken, FormRowContent>()

    for (component in parent.components) {
      val child = component as? JComponent
      val childMarks = child?.let { marks[it] }
      val rowMark = childMarks?.row
      if (child == null || childMarks == null || rowMark == null) {
        reportOrphan(component)
        continue
      }
      val row = rows.getOrPut(rowMark.token) {
        FormRowContent(rowMark.indent, rowMark.resizable, rowMark.topGap, rowMark.bottomGap, rowMark.labelPosition)
          .also { itemsOf(rowMark.group, root, groups) += it }
      }
      when (val mark = childMarks.mark) {
        // The only label a form makes is the one FormRow asks for, and that is a JLabel.
        is FormRowMark -> row.label = child as JLabel
        // The only comment a form makes is the one FormRow asks for, and that is a DslLabel.
        is FormCommentMark -> row.comment = child as DslLabel
        is FormCellMark -> row.cells += FormCellContent(child, mark)
        null -> row.cells += FormCellContent(child, FormCellMark())
      }
    }

    return root
  }

  /**
   * Reported the way the rest of the grid reports a form it cannot make sense of: this runs from the layout
   * of a page, and a page missing one component is worth more to whoever is looking at it than an exception
   * thrown again on every measurement.
   */
  private fun reportOrphan(component: Component) {
    errorInInternalOrLogWarn(
      "Every component of a form belongs to a row. Emit this one inside FormRow { ... }: $component"
    )
  }

  /**
   * The items of the container [token] holds, creating the group - and any group it is nested in - the first
   * time one of its children is seen. `null` is the form itself.
   */
  private fun itemsOf(
    token: FormGroupToken?,
    root: MutableList<FormItem>,
    groups: MutableMap<FormGroupToken, FormGroupContent>,
  ): MutableList<FormItem> {
    if (token == null) return root
    groups[token]?.let { return it.items }

    val group = FormGroupContent(token.indent)
    itemsOf(token.parent, root, groups) += group
    groups[token] = group
    return group.items
  }
}

/**
 * Extract text without html tags
 */
// Copied from com.intellij.ui.dsl.builder.impl.CellImpl.getPlainText.
@Suppress("HardCodedStringLiteral")
private fun DslLabel.getPlainText(): @Nls String? {
  val document = document ?: return null
  try {
    val result = document.getText(0, document.length) ?: return null
    return result.trim().takeIf { it.isNotEmpty() }
  }
  catch (_: BadLocationException) {
    return null
  }
}

/** The row a component stands in, if any, and its own mark. */
private class FormChildMarks {
  var row: FormRowMark? = null
  var mark: FormMark? = null

  private var lastAutoCalculatedAccessibleDescription: @Nls String? = null

  /**
   * Gives [component] the text of [comment] as its accessible description, by `CellImpl`'s rule: a description
   * set from another place is kept.
   */
  // Matches com.intellij.ui.dsl.builder.impl.CellImpl.updateAccessibleContextDescription.
  fun updateAccessibleContextDescription(component: JComponent, comment: DslLabel?) {
    val accessibleContext = component.accessibleContext ?: return
    val currentDescription = accessibleContext.accessibleDescription

    if (currentDescription != null && currentDescription != lastAutoCalculatedAccessibleDescription) {
      // Description is set from another place, don't change it
      return
    }

    lastAutoCalculatedAccessibleDescription = comment?.getPlainText()

    accessibleContext.accessibleDescription = lastAutoCalculatedAccessibleDescription
  }
}

/** One item of a form or of a group: a row, or a group taking a row of its own. */
internal sealed interface FormItem

internal class FormRowContent(
  val indent: Int,
  val resizable: Boolean,
  val topGap: FormGap?,
  val bottomGap: FormGap?,
  val labelPosition: FormLabelPosition,
) : FormItem {
  var label: JLabel? = null
  var comment: DslLabel? = null
  val cells: MutableList<FormCellContent> = mutableListOf()
}

internal class FormGroupContent(val indent: Int) : FormItem {
  val items: MutableList<FormItem> = mutableListOf()
}

/** One control of a row, and what it asked of the cell it is given. */
internal class FormCellContent(
  val component: JComponent,
  val mark: FormCellMark,
)
