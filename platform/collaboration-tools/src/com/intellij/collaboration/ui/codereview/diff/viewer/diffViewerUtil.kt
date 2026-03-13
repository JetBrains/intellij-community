// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewRendererFactory
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.codereview.editor.RendererFactory
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil
import com.intellij.collaboration.ui.codereview.editor.controlInlaysIn
import com.intellij.collaboration.ui.codereview.editor.renderInlays
import com.intellij.collaboration.util.HashingUtil
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.LineCol
import com.intellij.diff.util.Side
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Subscribe to [vmsFlow] and show inlays with renderers from [rendererFactory] on proper lines in viewer editors
 *
 * @param VM - inlay viemodel
 */
@ApiStatus.Experimental
fun <VM : DiffMapped> DiffViewerBase.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  when (this) {
    is SimpleOnesideDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    is UnifiedDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    is TwosideTextDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    else -> return
  }
}


@ApiStatus.Experimental
private fun <VM : DiffMapped> SimpleOnesideDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()
  val vmsForEditor = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == side }?.second
      }
    }
  }
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

private fun <VM : DiffMapped> UnifiedDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()
  val vmsForEditor = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        transferLineToOneside(loc.first, loc.second).takeIf { it >= 0 }
      }
    }
  }
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

private fun <VM : DiffMapped> TwosideTextDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()

  val vmsForEditor1 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == Side.LEFT }?.second
      }
    }
  }
  editor1.controlInlaysIn(cs, vmsForEditor1, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })

  val vmsForEditor2 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == Side.RIGHT }?.second
      }
    }
  }
  editor2.controlInlaysIn(cs, vmsForEditor2, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

/**
 * Create editor models for diff editors via [modelFactory] and show inlays and gutter controls
 * Inlays are created via [rendererFactory]
 *
 * @param M editor inlays and controls model
 * @param I inlay model
 */
@ApiStatus.Experimental
suspend fun <M, I> DiffViewerBase.showCodeReview(
  modelFactory: CoroutineScope.(locationToLine: (DiffLineLocation) -> Int?, lineToLocation: (Int) -> DiffLineLocation?) -> M,
  rendererFactory: RendererFactory<I, JComponent>,
): Nothing where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  showCodeReview { editor, _, locationToLine, lineToLocation, _ ->
    coroutineScope {
      val model = modelFactory(locationToLine, lineToLocation)
      editor.showCodeReview(model, rendererFactory)
    }
  }
}

typealias EditorModelFactory<M> = CoroutineScope.(
  editor: Editor,
  side: Side?,
  locationToLine: (DiffLineLocation) -> Int?,
  lineToLocation: (Int) -> DiffLineLocation?,
  lineToUnified: (Int) -> Pair<Int, Int>,
) -> M

/**
 * Create editor models for diff editors via [modelFactory] and show inlays and gutter controls
 * Inlays are created via [rendererFactory]
 *
 * @param M editor inlays and controls model
 * @param I inlay model
 */
@ApiStatus.Experimental
suspend fun <M, I> DiffViewerBase.showCodeReview(
  modelFactory: EditorModelFactory<M>,
  rendererFactory: RendererFactory<I, JComponent>,
): Nothing where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  showCodeReview { editor, side, locationToLine, lineToLocation, lineToUnified ->
    coroutineScope {
      val model = modelFactory(editor, side, locationToLine, lineToLocation, lineToUnified)
      editor.showCodeReview(model, rendererFactory)
    }
  }
}

typealias EditorCodeReviewRenderer = suspend (
  editor: EditorEx,
  side: Side?,
  locationToLine: (DiffLineLocation) -> Int?,
  lineToLocation: (Int) -> DiffLineLocation?,
  lineToUnified: (Int) -> Pair<Int, Int>,
) -> Nothing

suspend fun DiffViewerBase.showCodeReview(editorRenderer: EditorCodeReviewRenderer): Nothing {
  val viewer = this
  withContext(Dispatchers.EDT + CoroutineName("Code review diff UI")) {
    supervisorScope {
      var prevJob: Job? = null
      viewerReadyFlow().collect {
        prevJob?.cancelAndJoinSilently()
        if (!it) return@collect

        when (viewer) {
          is SimpleOnesideDiffViewer -> {
            prevJob = launchNow {
              editorRenderer(
                viewer.editor,
                viewer.side,
                { loc -> loc.takeIf { it.first == viewer.side }?.second },
                { lineIdx ->
                  DiffLineLocation(viewer.side, lineIdx).takeUnless {
                    ReviewInEditorUtil.isLastBlankLine(editor.document,
                                                       lineIdx)
                  }
                },
                { line -> if (viewer.side == Side.LEFT) line to -1 else -1 to line }
              )
            }
          }
          is UnifiedDiffViewer -> {
            prevJob = launchNow {
              editorRenderer(
                viewer.editor,
                null,
                { (side, lineIdx) -> viewer.transferLineToOnesideStrict(side, lineIdx).takeIf { it >= 0 } },
                { lineIdx ->
                  val (indices, side) = viewer.transferLineFromOnesideStrict(lineIdx) ?: return@editorRenderer null
                  val sideLineIdx = side.select(indices).takeIf { it >= 0 } ?: return@editorRenderer null
                  // Check line against the document of original side
                  if (ReviewInEditorUtil.isLastBlankLine(viewer.getDocument(side), sideLineIdx)) return@editorRenderer null
                  side to sideLineIdx
                },
                { line ->
                  val (leftLine, rightLine) = viewer.transferLineFromOneside(line).first
                  leftLine to rightLine
                }
              )
            }
          }
          is TwosideTextDiffViewer -> {
            prevJob = launchNow {
              launchNow {
                editorRenderer(
                  viewer.editor1,
                  Side.LEFT,
                  { (side, lineIdx) -> lineIdx.takeIf { side == Side.LEFT } },
                  { lineIdx ->
                    DiffLineLocation(Side.LEFT, lineIdx).takeUnless {
                      ReviewInEditorUtil.isLastBlankLine(editor1.document,
                                                         lineIdx)
                    }
                  },
                  { line -> line to viewer.transferPosition(Side.RIGHT, LineCol(line, 0)).line }
                )
              }
              launchNow {
                editorRenderer(
                  viewer.editor2,
                  Side.RIGHT,
                  { (side, lineIdx) -> lineIdx.takeIf { side == Side.RIGHT } },
                  { lineIdx ->
                    DiffLineLocation(Side.RIGHT, lineIdx).takeUnless {
                      ReviewInEditorUtil.isLastBlankLine(editor2.document,
                                                         lineIdx)
                    }
                  },
                  { line -> viewer.transferPosition(Side.LEFT, LineCol(line, 0)).line to line }
                )
              }
            }
          }
        }
      }
    }
    awaitCancellation()
  }
}

suspend fun <I, M> EditorEx.showCodeReview(model: M, rendererFactory: RendererFactory<I, JComponent>): Nothing
  where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  val editor = this
  coroutineScope {
    launchNow {
      CodeReviewEditorGutterControlsRenderer.render(model, editor)
    }

    launchNow {
      renderInlays(model.inlays, HashingUtil.mappingStrategy(CodeReviewInlayModel::key)) { rendererFactory(it) }
    }

    putUserData(CodeReviewCommentableEditorModel.KEY, model)
    if (model is CodeReviewNavigableEditorViewModel) {
      putUserData(CodeReviewNavigableEditorViewModel.KEY, model)
    }
    try {
      awaitCancellation()
    }
    finally {
      putUserData(CodeReviewCommentableEditorModel.KEY, null)
      if (model is CodeReviewNavigableEditorViewModel) {
        putUserData(CodeReviewNavigableEditorViewModel.KEY, null)
      }
    }
  }
}

internal fun <V : DiffViewerBase> V.viewerReadyFlow(): Flow<Boolean> {
  val isViewerGood: V.() -> Boolean = { !hasPendingRediff() }
  return callbackFlow {
    val listener = object : DiffViewerListener() {
      // for now this utility is only used for constant diffs
      // uncomment if diff can actually be changed on rediff
      /*override fun onBeforeRediff() {
      trySend(false)
    }*/

      override fun onAfterRediff() {
        trySend(isViewerGood())
      }
    }
    addListener(listener)
    send(isViewerGood())
    awaitClose {
      removeListener(listener)
    }
  }.withInitial(isViewerGood()).flowOn(Dispatchers.EDT).distinctUntilChanged()
}

interface DiffMapped {
  val location: Flow<DiffLineLocation?>
  val isVisible: Flow<Boolean>
}

private class Wrapper<VM : DiffMapped>(val vm: VM, val mapper: (DiffLineLocation) -> Int?) : EditorMapped {
  override val line: Flow<Int?> = vm.location.map { it?.let(mapper) }
  override val isVisible: Flow<Boolean> = vm.isVisible
}
