// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.viewer.EditorMapped
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

@ApiStatus.Experimental
class EditorLineInlaysController<VM : EditorMapped>(
  parentCs: CoroutineScope,
  inlayVms: Flow<Collection<VM>>,
  private val vmKeyExtractor: (VM) -> Any,
  private val inlaysManager: EditorComponentInlaysManager,
  private val componentFactory: CoroutineScope.(VM) -> JComponent
) {

  private val cs = parentCs.childScope(
    Dispatchers.Default +
    CoroutineName("com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController"))

  private val controllersByVmKey: MutableMap<Any, InlayController<VM>> = ConcurrentHashMap()

  init {
    cs.launchNow {
      inlayVms.collect { vms ->
        val vmsByKey = mutableMapOf<Any, VM>()

        for (vm in vms) {
          vmsByKey[vmKeyExtractor(vm)] = vm
        }

        // remove missing
        val iter = controllersByVmKey.iterator()
        while (iter.hasNext()) {
          val (key, ctrl) = iter.next()
          if (!vmsByKey.containsKey(key)) {
            iter.remove()
            ctrl.destroy()
          }
        }

        //add new
        for (vm in vms) {
          val key = vmKeyExtractor(vm)
          if (controllersByVmKey.contains(key)) continue
          controllersByVmKey[key] = InlayController(cs, inlaysManager, vm, componentFactory)
        }
      }
    }
  }
}

private class InlayController<VM : EditorMapped>(
  parentCs: CoroutineScope,
  inlaysManager: EditorComponentInlaysManager,
  vm: VM,
  componentFactory: CoroutineScope.(VM) -> JComponent
) {
  private val cs = parentCs.childScope(
    Dispatchers.Main.immediate +
    CoroutineName("com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController.InlayController"))

  private var inlay: Inlay<*>? = null

  init {
    cs.launchNow {
      combine(vm.line, vm.isVisible, ::Pair)
        .distinctUntilChanged()
        .collectLatest { (line, isVisible) ->
          coroutineScope {
            withContext(NonCancellable) {
              inlay?.let(Disposer::dispose)
            }
            if (line != null && isVisible) {
              val component = componentFactory(vm)
              inlay = inlaysManager.insertAfter(line, component)
              awaitCancellation()
            }
          }
        }
    }.invokeOnCompletion {
      inlay?.let(Disposer::dispose)
    }
  }

  suspend fun destroy() {
    cs.coroutineContext[Job]?.cancelAndJoin()
  }
}