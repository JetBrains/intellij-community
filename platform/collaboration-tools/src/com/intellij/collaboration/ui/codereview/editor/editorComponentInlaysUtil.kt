// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JPanel

interface EditorMapped {
  val line: Flow<Int?>
  val isVisible: Flow<Boolean>
}

private val LOG = Logger.getInstance("codereview.editor.inlays")

@ApiStatus.Experimental
fun <VM : EditorMapped> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
): Job {
  val editor = this
  val controllersByVmKey: MutableMap<Any, Job> = ConcurrentHashMap()

  return cs.launchNow(Dispatchers.Default + CoroutineName("Editor component inlays for $this")) {
    vmsFlow.collect { vms ->
      val vmsByKey = mutableMapOf<Any, VM>()

      for (vm in vms) {
        vmsByKey[vmKeyExtractor(vm)] = vm
      }

      // remove missing
      val iter = controllersByVmKey.iterator()
      while (iter.hasNext()) {
        val (key, job) = iter.next()
        if (!vmsByKey.containsKey(key)) {
          iter.remove()
          job.cancelAndJoinSilently()
        }
      }

      //add new
      for (vm in vms) {
        val key = vmKeyExtractor(vm)
        if (controllersByVmKey.contains(key)) continue
        controllersByVmKey[key] = cs.controlInlay(vm, editor, rendererFactory)
      }
    }
  }
}

private fun <VM : EditorMapped> CoroutineScope.controlInlay(
  vm: VM, editor: EditorEx, rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
): Job = launchNow(Dispatchers.Main) {
  var inlay: Inlay<*>? = null
  try {
    combine(vm.line, vm.isVisible, ::Pair)
      .distinctUntilChanged()
      .collectLatest { (line, isVisible) ->
        val currentInlay = inlay
        if (line != null && isVisible) {
          runCatching {
            val offset = editor.document.getLineEndOffset(line)
            if (currentInlay == null || !currentInlay.isValid || currentInlay.offset != offset) {
              currentInlay?.let(Disposer::dispose)
              inlay = editor.insertComponent(offset, rendererFactory(vm))
            }
          }.getOrLogException(LOG)
          awaitCancellation()
        }
        else if (currentInlay != null) {
          Disposer.dispose(currentInlay)
          inlay = null
        }
      }
  }
  finally {
    withContext(NonCancellable + ModalityState.any().asContextElement()) {
      inlay?.let(Disposer::dispose)
      inlay = null
    }
  }
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
fun EditorEx.insertComponent(offset: Int, renderer: ComponentInlayRenderer<JComponent>, priority: Int = 0): Inlay<*>? {
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