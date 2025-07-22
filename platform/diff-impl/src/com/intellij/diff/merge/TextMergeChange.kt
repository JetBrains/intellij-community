// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffGutterOperation.ModifiersRendererBuilder
import com.intellij.diff.util.DiffGutterOperation.WithModifiers
import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
class TextMergeChange @RequiresEdt constructor(
  val index: Int,
  val isImportChange: Boolean,
  val fragment: MergeLineFragment,
  conflictType: MergeConflictType,
  private val viewer: MergeThreesideViewer,
) : ThreesideDiffChangeBase(conflictType) {

  private val resolved: BooleanArray = BooleanArray(2)

  var isOnesideAppliedConflict: Boolean = false
    private set

  @get:ApiStatus.Internal
  var isResolvedWithAI: Boolean = false
    private set

  @RequiresEdt
  fun reinstallHighlighters() {
    destroyHighlighters()
    installHighlighters()

    destroyOperations()
    installOperations()

    viewer.repaintDividers()
  }

  @RequiresEdt
  fun setResolved(side: Side, value: Boolean) {
    resolved[side.index] = value

    if (isResolved) {
      destroyInnerHighlighters()
    }
    else {
      // Destroy only resolved side to reduce blinking
      val document = viewer.getEditor(side.select(ThreeSide.LEFT, ThreeSide.RIGHT)).getDocument()
      for (highlighter in innerHighlighters) {
        if (document == highlighter.getDocument()) {
          highlighter.dispose() // it's OK to call dispose() few times
        }
      }
    }
  }

  val isResolved: Boolean
    get() = resolved[0] && resolved[1]

  fun isResolved(side: Side): Boolean {
    return side.select(resolved)
  }

  fun markOnesideAppliedConflict() {
    isOnesideAppliedConflict = true
  }

  @ApiStatus.Internal
  fun markChangeResolvedWithAI() {
    isResolvedWithAI = true
  }

  override fun isResolved(side: ThreeSide): Boolean = when (side) {
    ThreeSide.LEFT -> isResolved(Side.LEFT)
    ThreeSide.BASE -> isResolved
    ThreeSide.RIGHT -> isResolved(Side.RIGHT)
  }

  val resultStartLine: Int
    get() = viewer.model.getLineStart(index)

  val resultEndLine: Int
    get() = viewer.model.getLineEnd(index)

  override fun getStartLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return resultStartLine
    return fragment.getStartLine(side)
  }

  override fun getEndLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return resultEndLine
    return fragment.getEndLine(side)
  }

  override val diffType: TextDiffType
    get() {
      val baseType = super.diffType
      if (!isResolvedWithAI) return baseType

      return AIResolvedDiffType(baseType)
    }

  override fun getEditor(side: ThreeSide): Editor {
    return viewer.getEditor(side)
  }

  @set:RequiresEdt
  public override var innerFragments: MergeInnerDifferences? = null
    set(innerFragments) {
      if (field == null && innerFragments == null) return
      field = innerFragments

      reinstallHighlighters()

      destroyInnerHighlighters()
      installInnerHighlighters()
    }

  //
  // Gutter actions
  //
  @RequiresEdt
  override fun installOperations() {
    if (viewer.isExternalOperationInProgress) return

    operations.addAllIfNotNull(
      createResolveOperation(),
      createAcceptOperation(Side.LEFT, OperationType.APPLY),
      createAcceptOperation(Side.LEFT, OperationType.IGNORE),
      createAcceptOperation(Side.RIGHT, OperationType.APPLY),
      createAcceptOperation(Side.RIGHT, OperationType.IGNORE),
      createResetOperation()
    )
  }

  private fun createOperation(side: ThreeSide, builder: ModifiersRendererBuilder): DiffGutterOperation? {
    if (isResolved(side)) return null

    val editor = viewer.getEditor(side)
    val offset = DiffGutterOperation.lineToOffset(editor, getStartLine(side))

    return WithModifiers(editor, offset, viewer.modifierProvider, builder)
  }

  private fun createResolveOperation(): DiffGutterOperation? {
    return createOperation(ThreeSide.BASE, ModifiersRendererBuilder { _: Boolean, _: Boolean, _: Boolean -> createResolveRenderer() })
  }

  private fun createAcceptOperation(versionSide: Side, type: OperationType): DiffGutterOperation? {
    val side = versionSide.select(ThreeSide.LEFT, ThreeSide.RIGHT)
    return createOperation(side, ModifiersRendererBuilder(fun(ctrlPressed: Boolean, _: Boolean, _: Boolean): GutterIconRenderer? {
      if (!isChange(versionSide)) return null
      if (type == OperationType.APPLY) {
        return createApplyRenderer(versionSide, ctrlPressed)
      }
      else {
        return createIgnoreRenderer(versionSide, ctrlPressed)
      }
    }))
  }

  private fun createResetOperation(): DiffGutterOperation? {
    if (!isResolved || !isResolvedWithAI) return null

    val editor = viewer.getEditor(ThreeSide.BASE)
    val offset = DiffGutterOperation.lineToOffset(editor, getStartLine(ThreeSide.BASE))


    return DiffGutterOperation.Simple(editor, offset, DiffGutterOperation.RendererBuilder {
      createIconRenderer(DiffBundle.message("action.presentation.diff.revert.text"), AllIcons.Diff.Revert, false, Runnable {
        viewer.executeMergeCommand(DiffBundle.message("merge.dialog.reset.change.command"),
                                   mutableListOf(this),
                                   Runnable { viewer.resetResolvedChange(this) })
      })
    })
  }

  private fun createApplyRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (isResolved(side)) return null
    val icon = if (isOnesideAppliedConflict) DiffUtil.getArrowDownIcon(side) else DiffUtil.getArrowIcon(side)
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), icon, isConflict, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.accept.change.command"),
                                 mutableListOf(this),
                                 Runnable { viewer.replaceSingleChange(this, side, modifier) })
    })
  }

  private fun createIgnoreRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (isResolved(side)) return null
    return createIconRenderer(DiffBundle.message("action.presentation.merge.ignore.text"), AllIcons.Diff.Remove, isConflict, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.ignore.change.command"),
                                 mutableListOf(this),
                                 Runnable { viewer.ignoreChange(this, side, modifier) })
    })
  }

  private fun createResolveRenderer(): GutterIconRenderer? {
    if (!isConflict || !viewer.canResolveChangeAutomatically(this, ThreeSide.BASE)) return null

    return createIconRenderer(DiffBundle.message("action.presentation.merge.resolve.text"), AllIcons.Diff.MagicResolve, false, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.resolve.conflict.command"),
                                 mutableListOf(this),
                                 Runnable { viewer.resolveSingleChangeAutomatically(this, ThreeSide.BASE) })
    })
  }

  private enum class OperationType {
    APPLY, IGNORE
  }

  //
  // State
  //
  fun storeState(): State {
    return State(
      index,
      resultStartLine,
      resultEndLine,

      resolved[0],
      resolved[1],

      isOnesideAppliedConflict,
      isResolvedWithAI)
  }

  fun restoreState(state: State) {
    resolved[0] = state.resolved1
    resolved[1] = state.resolved2

    isOnesideAppliedConflict = state.onesideAppliedConflict
    isResolvedWithAI = state.isResolvedByAI
  }

  @ApiStatus.Internal
  fun resetState() {
    resolved[0] = false
    resolved[1] = false
    isOnesideAppliedConflict = false
    isResolvedWithAI = false
  }

  class State(
    index: Int,
    startLine: Int,
    endLine: Int,
    val resolved1: Boolean,
    val resolved2: Boolean,
    val onesideAppliedConflict: Boolean,
    val isResolvedByAI: Boolean,
  ) : MergeModelBase.State(index, startLine, endLine)

  init {
    reinstallHighlighters()
  }

  private class AIResolvedDiffType(private val baseType: TextDiffType) : TextDiffType {
    override fun getName(): String {
      return baseType.getName()
    }

    override fun getColor(editor: Editor?): Color {
      return AI_COLOR
    }

    override fun getIgnoredColor(editor: Editor?): Color {
      return baseType.getIgnoredColor(editor)
    }

    override fun getMarkerColor(editor: Editor?): Color? {
      return baseType.getMarkerColor(editor)
    }
  }

  companion object {
    private fun createIconRenderer(
      text: @NlsContexts.Tooltip String,
      icon: Icon,
      ctrlClickVisible: Boolean,
      perform: Runnable,
    ): GutterIconRenderer {
      val appendix: @Nls String? = if (ctrlClickVisible) DiffBundle.message("tooltip.merge.ctrl.click.to.resolve.conflict") else null
      val tooltipText = DiffUtil.createTooltipText(text, appendix)
      return object : DiffGutterRenderer(icon, tooltipText) {
        override fun handleMouseClick() {
          perform.run()
        }
      }
    }

    private val AI_COLOR = JBColor(0x834DF0, 0xA571E6) // TODO: move to platform utils
  }
}
