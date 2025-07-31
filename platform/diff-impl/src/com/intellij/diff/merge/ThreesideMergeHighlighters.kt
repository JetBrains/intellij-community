// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.tools.simple.DiffViewerHighlighters
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
internal class ThreesideMergeHighlighters(
  override val change: TextMergeChange,
  innerFragments: MergeInnerDifferences? = null,
  private val viewer: MergeThreesideViewer,
) : DiffViewerHighlighters(change, innerFragments, viewer::getEditor) {
  init {
    reinstallAll()
  }

  @set:RequiresEdt
  public override var innerFragments: MergeInnerDifferences? = null
    set(innerFragments) {
      if (field == null && innerFragments == null) return
      field = innerFragments

      reinstallAll()

      destroyInnerHighlighters()
      installInnerHighlighters()
    }

  fun updateOperations(force: Boolean) {
    for (operation in operations) {
      operation.update(force)
    }
  }

  @RequiresEdt
  override fun reinstallAll() {
    destroyHighlighters()
    installHighlighters()

    destroyOperations()
    installOperations()

    viewer.repaintDividers()
  }

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

  // operations
  private fun createOperation(side: ThreeSide, builder: DiffGutterOperation.ModifiersRendererBuilder): DiffGutterOperation? {
    if (change.isResolved(side)) return null

    val editor = viewer.getEditor(side)
    val offset = DiffGutterOperation.lineToOffset(editor, change.getStartLine(side))

    return DiffGutterOperation.WithModifiers(editor, offset, viewer.modifierProvider, builder)
  }

  private fun createResolveOperation(): DiffGutterOperation? {
    return createOperation(ThreeSide.BASE,
                           DiffGutterOperation.ModifiersRendererBuilder { _: Boolean, _: Boolean, _: Boolean -> createResolveRenderer() })
  }

  private fun createAcceptOperation(versionSide: Side, type: OperationType): DiffGutterOperation? {
    val side = versionSide.select(ThreeSide.LEFT, ThreeSide.RIGHT)
    return createOperation(side, DiffGutterOperation.ModifiersRendererBuilder(
      fun(ctrlPressed: Boolean, _: Boolean, _: Boolean): GutterIconRenderer? {
        if (!change.isChange(versionSide)) return null
        if (type == OperationType.APPLY) {
          return createApplyRenderer(versionSide, ctrlPressed)
        }
        else {
          return createIgnoreRenderer(versionSide, ctrlPressed)
        }
      }))
  }

  private fun createResetOperation(): DiffGutterOperation? {
    if (!change.isResolved || !change.isResolvedWithAI) return null

    val editor = viewer.getEditor(ThreeSide.BASE)
    val offset = DiffGutterOperation.lineToOffset(editor, change.getStartLine(ThreeSide.BASE))


    return DiffGutterOperation.Simple(editor, offset, DiffGutterOperation.RendererBuilder {
      createIconRenderer(DiffBundle.message("action.presentation.diff.revert.text"), AllIcons.Diff.Revert, false, Runnable {
        viewer.executeMergeCommand(DiffBundle.message("merge.dialog.reset.change.command"),
                                   mutableListOf(change),
                                   Runnable { viewer.resetResolvedChange(change) })
      })
    })
  }

  private fun createApplyRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (change.isResolved(side)) return null
    val icon = if (change.isOnesideAppliedConflict) DiffUtil.getArrowDownIcon(side) else DiffUtil.getArrowIcon(side)
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), icon, change.isConflict, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.accept.change.command"),
                                 mutableListOf(change),
                                 Runnable { viewer.replaceSingleChange(change, side, modifier) })
    })
  }

  private fun createIgnoreRenderer(side: Side, modifier: Boolean): GutterIconRenderer? {
    if (change.isResolved(side)) return null
    return createIconRenderer(DiffBundle.message("action.presentation.merge.ignore.text"), AllIcons.Diff.Remove, change.isConflict, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.ignore.change.command"),
                                 mutableListOf(change),
                                 Runnable { viewer.ignoreChange(change, side, modifier) })
    })
  }

  private fun createResolveRenderer(): GutterIconRenderer? {
    if (!change.isConflict || !viewer.canResolveChangeAutomatically(change, ThreeSide.BASE)) return null

    return createIconRenderer(DiffBundle.message("action.presentation.merge.resolve.text"), AllIcons.Diff.MagicResolve, false, Runnable {
      viewer.executeMergeCommand(DiffBundle.message("merge.dialog.resolve.conflict.command"),
                                 mutableListOf(change),
                                 Runnable { viewer.resolveSingleChangeAutomatically(change, ThreeSide.BASE) })
    })
  }

  fun destroyInnerHighlighters(document: DocumentEx) {
    for (inner in innerHighlighters) {
      if (inner.document == document) {
        inner.dispose()
      }
    }
  }

  private enum class OperationType {
    APPLY, IGNORE
  }

  companion object {
    internal fun createIconRenderer(
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
  }
}