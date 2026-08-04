// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
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
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.IdentityHashMap
import javax.swing.JComponent

/**
 * The layout of a [FormPanel]: a platform grid worked out from what the panel's children say they are.
 *
 * A component cannot be given a cell as it arrives, because where it belongs depends on the whole form: the
 * column count is the widest row's, a group has columns of its own, and the space between two rows is the
 * larger of what the two ask for. So a component arrives saying where it was declared and what it plays there
 * - see [FormSlot] - and the grid is built from all of them together, as late as the first measurement and
 * again whenever they change.
 *
 * Where the cells then go is not decided here: the form is handed to [buildGridForm], the same code
 * `panel { }` is laid out by, so a page written in Compose is laid out by the rules every other settings page
 * is laid out by rather than by rules that resemble them.
 *
 * The components are untouched by a rebuild - they are never taken out of the panel and never recreated - so
 * what they hold, and the focus and the selection in them, survives a row appearing or disappearing above.
 */
internal class FormGridLayout : GridLayout() {

  /** What each component was added with, and whether the grid has yet to be built from them. */
  private val marks = IdentityHashMap<JComponent, Any?>()
  private var gridOutOfDate = false

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    // The grid registers its own cells through this method, and those carry a Constraints, so what tells the
    // two apart is the constraints themselves rather than who is adding the component.
    if (constraints is Constraints) {
      super.addLayoutComponent(comp, constraints)
      return
    }

    marks[checkJComponent(comp)] = constraints
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

      // Cleared first: the cells are registered through addLayoutComponent, which would otherwise take them
      // for components still to be placed and ask for the grid to be built again.
      gridOutOfDate = false
      resetRootGrid()

      // The spacing is read afresh here because two of its values are derived from the look and feel's
      // checkbox metrics, so a form built after a theme change is built to the new ones.
      val spacing = IntelliJSpacingConfiguration()
      buildGridForm(toGridForm(readItems(parent), spacing), RowsGridBuilder(checkJComponent(parent)), spacing)
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
    val controls = row.cells.mapIndexed { index, cell ->
      GridFormComponentCell(
        component = cell.component,
        comment = if (index == 0) comment else null,
        horizontalAlign = if (cell.fill) HorizontalAlign.FILL else HorizontalAlign.LEFT,
        rightGap = if (cell.smallGapAfter) RightGap.SMALL else null,
      )
    }
    val label = row.label
      ?: return GridFormRow(
        rowLayout = RowLayout.INDEPENDENT,
        // A row holding nothing but its comment has nothing to put the comment under, so the comment is
        // what the row holds - the row FormComment writes.
        cells = if (controls.isEmpty() && comment != null) listOf(toCommentCell(comment)) else controls,
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
   * [FormRowScope.FillWidth].
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
   * Reads the form's declared structure off the children of [parent]: each names the row it was declared in
   * and the group that row belongs to, so the rows and the groups are what the children say they are. They
   * arrive in the order they were composed in, which is the order they were declared in, so the items of a
   * group and the controls of a row come out in that order too.
   */
  private fun readItems(parent: Container): List<FormItem> {
    val root = mutableListOf<FormItem>()
    val groups = IdentityHashMap<FormGroupToken, FormGroupContent>()
    val rows = IdentityHashMap<FormRowToken, FormRowContent>()

    for (component in parent.components) {
      val child = component as? JComponent
      val slot = child?.let { marks[it] } as? FormSlot
      if (child == null || slot == null) {
        // Reported the way the rest of the grid reports a form it cannot make sense of: this runs from the
        // layout of a page, and a page missing one component is worth more to whoever is looking at it than
        // an exception thrown again on every measurement.
        errorInInternalOrLogWarn("Every component of a form belongs to a row. Emit this one inside FormRow { ... }: $component")
        continue
      }

      val row =
        rows.getOrPut(slot.row) {
          FormRowContent(slot.indent, slot.resizable, slot.topGap, slot.bottomGap)
            .also { itemsOf(slot.group, root, groups) += it }
        }

      when (slot.role) {
        FormRole.LABEL -> row.label = child
        FormRole.CONTROL -> row.cells += FormCellContent(child, slot.fill, slot.smallGapAfter)
        // The only comment a form makes is the one FormRow asks for, and that is a DslLabel.
        FormRole.COMMENT -> row.comment = child as DslLabel
      }
    }

    return root
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

/** One item of a form or of a group: a row, or a group taking a row of its own. */
internal sealed interface FormItem

internal class FormRowContent(
  val indent: Int,
  val resizable: Boolean,
  val topGap: FormGap?,
  val bottomGap: FormGap?,
) : FormItem {
  var label: JComponent? = null
  var comment: DslLabel? = null
  val cells: MutableList<FormCellContent> = mutableListOf()
}

internal class FormGroupContent(val indent: Int) : FormItem {
  val items: MutableList<FormItem> = mutableListOf()
}

/** One control of a row, and what it asked of the cell it is given. */
internal class FormCellContent(
  val component: JComponent,
  val fill: Boolean,
  val smallGapAfter: Boolean,
)
