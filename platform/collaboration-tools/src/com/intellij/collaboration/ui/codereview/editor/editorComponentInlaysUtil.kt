// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.combineStateIn
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.util.HashingUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory.createCustomHashingStrategyMap
import com.intellij.util.containers.CollectionFactory.createCustomHashingStrategySet
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

internal typealias RendererFactory<VM, C> = CoroutineScope.(VM) -> ComponentInlayRenderer<C>
internal typealias CodeReviewRendererFactory<VM> = CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer

@Deprecated("Use EditorMappedViewModel instead for explicit flow guarantees")
interface EditorMapped {
  val line: Flow<Int?>
  val isVisible: Flow<Boolean>
}

@ApiStatus.Experimental
interface EditorMappedViewModel : EditorMapped {
  override val line: StateFlow<Int?>
  override val isVisible: StateFlow<Boolean>
}

private val LOG = Logger.getInstance("codereview.editor.inlays")

@Deprecated("Use the suspending function renderInlays for thread safety",
            ReplaceWith("cs.launchNow(Dispatchers.Main) {\n" +
                        "  renderInlays(vmsFlow, HashingUtil.mappingStrategy(vmKeyExtractor), rendererFactory)\n" +
                        "}"))
fun <VM : EditorMapped> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>
): Job = cs.launchNow(Dispatchers.Main) {
  doRenderInlays(vmsFlow, HashingUtil.mappingStrategy(vmKeyExtractor), rendererFactory)
}

/**
 * Shows editor inlays constructed from view models in [vmsFlow] using [rendererFactory]
 * Does NOT guarantee the order
 *
 * @param vmHashingStrategy used to compare VMs to avoid unnecessary recreation of inlays
 */
@ApiStatus.Experimental
suspend fun <VM : EditorMappedViewModel> EditorEx.renderInlays(
  vmsFlow: Flow<Collection<VM>>,
  vmHashingStrategy: HashingStrategy<VM>,
  rendererFactory: RendererFactory<VM, JComponent>
): Nothing = doRenderInlays(vmsFlow, vmHashingStrategy, rendererFactory)

private suspend fun <VM : EditorMapped> EditorEx.doRenderInlays(
  vmsFlow: Flow<Collection<VM>>,
  vmHashingStrategy: HashingStrategy<VM>,
  rendererFactory: RendererFactory<VM, JComponent>
): Nothing {
  val editor = this
  withContext(Dispatchers.Main.immediate + CoroutineName("Editor component inlays for $this")) {
    val inlaysCs = this
    val controllersByVmKey = createCustomHashingStrategyMap<VM, Job>(vmHashingStrategy)
    val positionKeeper = EditorScrollingPositionKeeper(editor)
    vmsFlow.map {
      createCustomHashingStrategySet(vmHashingStrategy).apply { addAll(it) }
    }.collect {
      writeIntentReadAction {
        positionKeeper.savePosition()
      }

      // remove missing
      val iter = controllersByVmKey.iterator()
      while (iter.hasNext()) {
        val (key, job) = iter.next()
        if (!it.contains(key)) {
          iter.remove()
          job.cancelAndJoinSilently()
        }
      }

      //add new
      for (vm in it) {
        if (controllersByVmKey.containsKey(vm)) continue
        controllersByVmKey[vm] = inlaysCs.launchNow {
          controlInlay(vm, editor, rendererFactory)
        }
      }

      writeIntentReadAction {
        // immediately validate the editor to recalculate the size with inlays
        editor.contentComponent.validate()
        positionKeeper.restorePosition(true)
        editor.contentComponent.repaint()
      }
    }
    awaitCancellation()
  }
}

private suspend fun <VM : EditorMapped> controlInlay(vm: VM, editor: EditorEx, rendererFactory: RendererFactory<VM, JComponent>): Nothing {
  withContext(Dispatchers.Main.immediate + CoroutineName("Scope for code review component editor inlay for $vm")) {
    var inlay: Inlay<*>? = null
    try {
      val lineFlow = vm.line
      val visibleFlow = vm.isVisible
      if (lineFlow is StateFlow && visibleFlow is StateFlow) {
        // Can't do combine.stateIn bc combine doesn't handle UNDISPATCHED
        combineStateIn(this, lineFlow, visibleFlow, ::Pair)
      }
      else {
        combine(lineFlow, visibleFlow, ::Pair)
      }.distinctUntilChanged()
        .collect { (line, isVisible) ->
          writeIntentReadAction {
            val currentInlay = inlay
            if (line != null && isVisible) {
              runCatching {
                val offset = editor.document.getLineEndOffset(line)
                if (currentInlay == null || !currentInlay.isValid || currentInlay.offset != offset) {
                  currentInlay?.let(Disposer::dispose)
                  inlay = insertComponent(vm, rendererFactory, editor, offset)
                }
              }.getOrLogException(LOG)
            }
            else if (currentInlay != null) {
              Disposer.dispose(currentInlay)
              inlay = null
            }
          }
        }
      awaitCancellation()
    }
    finally {
      withContext(NonCancellable + ModalityState.any().asContextElement()) {
        inlay?.let(Disposer::dispose)
        inlay = null
      }
    }
  }
}

private fun <VM : EditorMapped> CoroutineScope.insertComponent(
  vm: VM,
  rendererFactory: RendererFactory<VM, JComponent>,
  editor: EditorEx,
  offset: Int
): Inlay<*>? {
  val inlayScope = childScope("Scope for code review component editor inlay at $offset")
  var newInlay: Inlay<*>? = null
  try {
    newInlay = editor.insertComponent(offset, inlayScope.rendererFactory(vm))?.also {
      inlayScope.cancelledWith(it)
    }
  }
  finally {
    if (newInlay == null) {
      inlayScope.cancel()
    }
  }
  return newInlay
}

// TODO: rework diff mode with aligned changes.
//  This mode uses Inlays and some comments may look bad with this mode enabled because of the positioning
/**
 * @param priority impacts the visual order in which inlays are displayed. Components with higher priority will be shown higher
 */
@RequiresEdt
fun EditorEx.insertComponentAfter(lineIndex: Int,
                                  component: JComponent,
                                  priority: Int = 0,
                                  rendererFactory: (Inlay<*>) -> GutterIconRenderer? = { null }): Inlay<*>? {
  val offset = document.getLineEndOffset(lineIndex)
  val renderer = object : CodeReviewComponentInlayRenderer(component) {
    override fun calcGutterIconRenderer(inlay: Inlay<*>): GutterIconRenderer? = rendererFactory(inlay)
  }
  return insertComponent(offset, renderer, priority)
}

@RequiresEdt
private fun EditorEx.insertComponent(offset: Int, renderer: ComponentInlayRenderer<JComponent>, priority: Int = 0): Inlay<*>? {
  val props = InlayProperties().priority(priority).relatesToPrecedingText(true)
  return addComponentInlay(offset, props, renderer)
}

// 52 for avatar and gaps
open class CodeReviewComponentInlayRenderer(private val actualComponent: JComponent)
  : ComponentInlayRenderer<JComponent>(wrapWithLimitedWidth(actualComponent, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + 52),
                                       ComponentInlayAlignment.FIT_VIEWPORT_WIDTH) {
  var isVisible: Boolean
    get() = actualComponent.isVisible
    set(value) {
      actualComponent.isVisible = value
    }
}

private fun wrapWithLimitedWidth(component: JComponent, width: Int): JComponent {
  return JPanel(null).apply {
    isOpaque = false
    // 52 for avatar and gaps
    val widthRestriction = DimensionRestrictions.ScalingConstant(width = width)
    layout = SizeRestrictedSingleComponentLayout().apply {
      prefSize = widthRestriction
      maxSize = widthRestriction
    }

    add(component)
  }
}