// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.combineStateIn
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.util.HashingUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory.createCustomHashingStrategyMap
import com.intellij.util.containers.CollectionFactory.createCustomHashingStrategySet
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

typealias RendererFactory<VM, C> = CoroutineScope.(VM) -> ComponentInlayRenderer<C>
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
            ReplaceWith("cs.launchNow(Dispatchers.EDT) {\n" +
                        "  renderInlays(vmsFlow, HashingUtil.mappingStrategy(vmKeyExtractor), rendererFactory)\n" +
                        "}"))
fun <VM : EditorMapped> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
): Job = cs.launchNow(Dispatchers.EDT) {
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
  rendererFactory: RendererFactory<VM, JComponent>,
): Nothing = doRenderInlays(vmsFlow, vmHashingStrategy, rendererFactory)

private suspend fun <VM : EditorMapped> EditorEx.doRenderInlays(
  vmsFlow: Flow<Collection<VM>>,
  vmHashingStrategy: HashingStrategy<VM>,
  rendererFactory: RendererFactory<VM, JComponent>,
): Nothing {
  val editor = this
  withContext(Dispatchers.EdtImmediate + CoroutineName("Editor component inlays for $this")) {
    val inlaysCs = this
    val controllersByVmKey = createCustomHashingStrategyMap<VM, Job>(vmHashingStrategy)
    val positionKeeper = EditorScrollingPositionKeeper(editor)
    vmsFlow.collect { list ->
      val set = createCustomHashingStrategySet(vmHashingStrategy).apply { addAll(list) }

      writeIntentReadAction {
        positionKeeper.savePosition()
      }

      // remove missing
      val iter = controllersByVmKey.iterator()
      while (iter.hasNext()) {
        val (key, job) = iter.next()
        if (!set.contains(key)) {
          iter.remove()
          job.cancelAndJoinSilently()
        }
      }

      //add new
      for (vm in list) {
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
  withContext(Dispatchers.EdtImmediate + CoroutineName("Scope for code review editor inlay for $vm")) {
    var scopeAndRenderer: Pair<CoroutineScope, ComponentInlayRenderer<JComponent>>? = null

    fun createRenderer(): ComponentInlayRenderer<JComponent> {
      val rendererCs = childScope("Scope for code review editor inlay renderer for $vm")
      val renderer = rendererFactory(rendererCs, vm)
      scopeAndRenderer = rendererCs to renderer
      return renderer
    }

    val lineFlow = vm.line
    val visibleFlow = vm.isVisible
    if (lineFlow is StateFlow && visibleFlow is StateFlow) {
      // Can't do combine.stateIn bc combine doesn't handle UNDISPATCHED
      combineStateIn(this, lineFlow, visibleFlow, ::Pair)
    }
    else {
      combine(lineFlow, visibleFlow, ::Pair)
    }.distinctUntilChanged().collectScoped { (line, isVisible) ->
      if (line != null) {
        if (isVisible) {
          val renderer = scopeAndRenderer?.second ?: createRenderer()
          editor.renderComponent(renderer, line)
        }
      }
      else {
        scopeAndRenderer?.first?.cancelAndJoinSilently()
        scopeAndRenderer = null
      }
    }
    awaitCancellation()
  }
}

private suspend fun EditorEx.renderComponent(renderer: ComponentInlayRenderer<JComponent>, line: Int): Nothing? =
  withContext(Dispatchers.EdtImmediate + CoroutineName("Scope for code review editor inlay for $renderer at line $line")) {
    val inlay = runCatching {
      writeIntentReadAction {
        if (DiffUtil.getLineCount(document) <= line) return@writeIntentReadAction null
        val offset = document.getLineEndOffset(line)
        insertComponent(offset, renderer)
      }
    }.getOrLogException(LOG)

    if (inlay == null) {
      LOG.warn("Failed to insert inlay into $this at line $line")
      return@withContext null
    }
    else {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable + ModalityState.any().asContextElement()) {
          Disposer.dispose(inlay)
        }
      }
    }
  }

// TODO: rework diff mode with aligned changes.
//  This mode uses Inlays and some comments may look bad with this mode enabled because of the positioning
/**
 * @param priority impacts the visual order in which inlays are displayed. Components with higher priority will be shown higher
 */
@RequiresEdt
fun EditorEx.insertComponentAfter(
  lineIndex: Int,
  component: JComponent,
  priority: Int = 0,
  rendererFactory: (Inlay<*>) -> GutterIconRenderer? = { null },
): Inlay<*>? {
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