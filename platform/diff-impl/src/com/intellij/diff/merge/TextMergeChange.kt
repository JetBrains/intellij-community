// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.simple.ThreesideDiffChangeBase
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffGutterOperation.ModifiersRendererBuilder
import com.intellij.diff.util.DiffGutterOperation.WithModifiers
import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

@ApiStatus.Internal
class TextMergeChange @RequiresEdt constructor(
//
  // Getters
  //
  val index: Int,
  val isImportChange: Boolean,
  val fragment: MergeLineFragment,
  conflictType: MergeConflictType,
  private val myMergeViewer: TextMergeViewer,
) : ThreesideDiffChangeBase(conflictType) {
  protected val myViewer: MergeThreesideViewer

  protected val myResolved: BooleanArray = BooleanArray(2)
  var isOnesideAppliedConflict: Boolean = false
    private set

  @get:ApiStatus.Internal
  var isResolvedWithAI: Boolean = false
    private set

  private var myInnerFragments: MergeInnerDifferences? = null // warning: might be out of date

  @RequiresEdt
  fun reinstallHighlighters() {
    destroyHighlighters()
    installHighlighters()

    destroyOperations()
    installOperations()

    myViewer.repaintDividers()
  }

  @RequiresEdt
  fun setResolved(side: Side, value: Boolean) {
    myResolved[side.index] = value

    if (this.isResolved) {
      destroyInnerHighlighters()
    }
    else {
      // Destroy only resolved side to reduce blinking
      val document: Document = myViewer.getEditor(side.select<ThreeSide>(ThreeSide.LEFT, ThreeSide.RIGHT)).getDocument()
      for (highlighter in myInnerHighlighters) {
        if (document == highlighter.getDocument()) {
          highlighter.dispose() // it's OK to call dispose() few times
        }
      }
    }
  }

  val isResolved: Boolean
    get() = myResolved[0] && myResolved[1]

  fun isResolved(side: Side): Boolean {
    return side.select(myResolved)
  }

  fun markOnesideAppliedConflict() {
    this.isOnesideAppliedConflict = true
  }

  @ApiStatus.Internal
  fun markChangeResolvedWithAI() {
    this.isResolvedWithAI = true
  }

  override fun isResolved(side: ThreeSide): Boolean {
    return when (side) {
      ThreeSide.LEFT -> isResolved(Side.LEFT)
      ThreeSide.BASE -> this.isResolved
      ThreeSide.RIGHT -> isResolved(Side.RIGHT)
    }
  }

  val startLine: Int
    get() = myViewer.getModel().getLineStart(this.index)

  val endLine: Int
    get() = myViewer.getModel().getLineEnd(this.index)

  override fun getStartLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return this.startLine
    return fragment.getStartLine(side)
  }

  override fun getEndLine(side: ThreeSide): Int {
    if (side == ThreeSide.BASE) return this.endLine
    return fragment.getEndLine(side)
  }

  override fun getDiffType(): TextDiffType {
    val baseType = super.getDiffType()
    if (!this.isResolvedWithAI) return baseType

    return MyAIResolvedDiffType(baseType)
  }

  override fun getEditor(side: ThreeSide): Editor {
    return myViewer.getEditor(side)
  }

  override fun getInnerFragments(): MergeInnerDifferences? {
    return myInnerFragments
  }

  @RequiresEdt
  fun setInnerFragments(innerFragments: MergeInnerDifferences?) {
    if (myInnerFragments == null && innerFragments == null) return
    myInnerFragments = innerFragments

    reinstallHighlighters()

    destroyInnerHighlighters()
    installInnerHighlighters()
  }

  //
  // Gutter actions
  //
  @RequiresEdt
  override fun installOperations() {
    if (myViewer.isExternalOperationInProgress()) return

    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createResolveOperation())
    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createAcceptOperation(Side.LEFT, OperationType.APPLY))
    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createAcceptOperation(Side.LEFT, OperationType.IGNORE))
    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createAcceptOperation(Side.RIGHT, OperationType.APPLY))
    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createAcceptOperation(Side.RIGHT, OperationType.IGNORE))
    ContainerUtil.addIfNotNull<DiffGutterOperation?>(myOperations, createResetOperation())
  }

  private fun createOperation(side: ThreeSide, builder: ModifiersRendererBuilder): DiffGutterOperation? {
    if (isResolved(side)) return null

    val editor = myViewer.getEditor(side)
    val offset = DiffGutterOperation.lineToOffset(editor, getStartLine(side))

    return WithModifiers(editor, offset, myViewer.getModifierProvider(), builder)
  }

  private fun createResolveOperation(): DiffGutterOperation? {
    return createOperation(
      ThreeSide.BASE,
      ModifiersRendererBuilder { ctrlPressed: Boolean, shiftPressed: Boolean, altPressed: Boolean -> createResolveRenderer() })
  }

  private fun createAcceptOperation(versionSide: Side, type: OperationType): DiffGutterOperation? {
    val side: ThreeSide? = versionSide.select<ThreeSide>(ThreeSide.LEFT, ThreeSide.RIGHT)
    return createOperation(side!!, ModifiersRendererBuilder { ctrlPressed: Boolean, shiftPressed: Boolean, altPressed: Boolean ->
      if (!isChange(versionSide)) return@createOperation null
      if (type == OperationType.APPLY) {
        return@createOperation createApplyRenderer(versionSide, ctrlPressed)
      }
      else {
        return@createOperation createIgnoreRenderer(versionSide, ctrlPressed)
      }
    })
  }

  private fun createResetOperation(): DiffGutterOperation? {
    if (!this.isResolved || !this.isResolvedWithAI) return null

    val editor = myViewer.getEditor(ThreeSide.BASE)
    val offset = DiffGutterOperation.lineToOffset(editor, getStartLine(ThreeSide.BASE))


    return DiffGutterOperation.Simple(editor, offset, DiffGutterOperation.RendererBuilder {
      createIconRenderer(DiffBundle.message("action.presentation.diff.revert.text"), AllIcons.Diff.Revert, false, Runnable {
        myViewer.executeMergeCommand(
          DiffBundle.message("merge.dialog.reset.change.command"),
          mutableListOf<TextMergeChange?>(this),
          Runnable { myViewer.resetResolvedChange(this) })
      })
    })
  }

  private fun createApplyRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (isResolved(side)) return null
    val icon = if (this.isOnesideAppliedConflict) DiffUtil.getArrowDownIcon(side) else DiffUtil.getArrowIcon(side)
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), icon, isConflict(), Runnable {
      myViewer.executeMergeCommand(
        DiffBundle.message("merge.dialog.accept.change.command"),
        mutableListOf<TextMergeChange?>(this),
        Runnable { myViewer.replaceSingleChange(this, side, modifier) })
    })
  }

  private fun createIgnoreRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (isResolved(side)) return null
    return createIconRenderer(
      DiffBundle.message("action.presentation.merge.ignore.text"),
      AllIcons.Diff.Remove,
      isConflict(),
      Runnable {
        myViewer.executeMergeCommand(
          DiffBundle.message("merge.dialog.ignore.change.command"), mutableListOf<TextMergeChange?>(this),
          Runnable { myViewer.ignoreChange(this, side, modifier) })
      })
  }

  private fun createResolveRenderer(): GutterIconRenderer? {
    if (!this.isConflict() || !myViewer.canResolveChangeAutomatically(this, ThreeSide.BASE)) return null

    return createIconRenderer(
      DiffBundle.message("action.presentation.merge.resolve.text"),
      AllIcons.Diff.MagicResolve,
      false,
      Runnable {
        myViewer.executeMergeCommand(
          DiffBundle.message("merge.dialog.resolve.conflict.command"), mutableListOf<TextMergeChange?>(this),
          Runnable { myViewer.resolveSingleChangeAutomatically(this, ThreeSide.BASE) })
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
      this.index,
      this.startLine,
      this.endLine,

      myResolved[0],
      myResolved[1],

      this.isOnesideAppliedConflict,
      this.isResolvedWithAI
    )
  }

  fun restoreState(state: State) {
    myResolved[0] = state.myResolved1
    myResolved[1] = state.myResolved2

    this.isOnesideAppliedConflict = state.myOnesideAppliedConflict
    this.isResolvedWithAI = state.myIsResolvedByAI
  }

  @ApiStatus.Internal
  fun resetState() {
    myResolved[0] = false
    myResolved[1] = false
    this.isOnesideAppliedConflict = false
    this.isResolvedWithAI = false
  }

  class State(
    index: Int,
    startLine: Int,
    endLine: Int,
    private val myResolved1: Boolean,
    private val myResolved2: Boolean,
    private val myOnesideAppliedConflict: Boolean,
    private val myIsResolvedByAI: Boolean,
  ) : MergeModelBase.State(index, startLine, endLine)

  init {
    myViewer = myMergeViewer.getViewer()

    reinstallHighlighters()
  }

  private class MyAIResolvedDiffType(private val myBaseType: TextDiffType) : TextDiffType {
    override fun getName(): String {
      return myBaseType.getName()
    }

    override fun getColor(editor: Editor?): Color {
      return AI_COLOR
    }

    override fun getIgnoredColor(editor: Editor?): Color {
      return myBaseType.getIgnoredColor(editor)
    }

    override fun getMarkerColor(editor: Editor?): Color? {
      return myBaseType.getMarkerColor(editor)
    }
  }

  companion object {
    private fun createIconRenderer(
      @NlsContexts.Tooltip text: @NlsContexts.Tooltip String,
      icon: Icon,
      ctrlClickVisible: Boolean,
      perform: Runnable,
    ): GutterIconRenderer {
      @Nls val appendix: @Nls String? =
        if (ctrlClickVisible) DiffBundle.message("tooltip.merge.ctrl.click.to.resolve.conflict") else null
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
