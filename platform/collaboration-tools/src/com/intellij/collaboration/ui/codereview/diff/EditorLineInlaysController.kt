// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.COMPONENT_SCOPE_KEY
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClientProperty
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

@ApiStatus.Experimental
class EditorLineInlaysController<VM : Any>(
  parentCs: CoroutineScope,
  inlayVms: Flow<Map<Int, List<VM>>>,
  private val vmKeyExtractor: (VM) -> Any,
  private val componentFactory: (CoroutineScope, VM) -> JComponent,
  private val inlaysManager: EditorComponentInlaysManager
) {

  private val cs = parentCs.childScope(Dispatchers.Main.immediate)
  private val componentByKey: MutableMap<Any, JComponent> = ConcurrentHashMap()

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      inlayVms.collect { vms ->
        val vmsByKey = mutableMapOf<Any, VM>()

        for ((line, vmsOnLine) in vms) {
          for (vm in vmsOnLine) {
            vmsByKey[getKey(line, vm)] = vm
          }
        }

        // remove missing
        val iter = componentByKey.iterator()
        while (iter.hasNext()) {
          val (key, component) = iter.next()
          if (!vmsByKey.containsKey(key)) {
            iter.remove()
            val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
            cs.launch {
              componentCs?.coroutineContext?.get(Job)?.cancelAndJoin()
            }
          }
        }

        //add new
        for ((line, vmsOnLine) in vms) {
          for (vm in vmsOnLine) {
            val key = getKey(line, vm)
            if (componentByKey.contains(key)) continue

            val component = insert(line, vm) ?: continue
            componentByKey[key] = component
          }
        }
      }
    }
  }

  private fun insert(line: Int, inlayVm: VM): JComponent? {
    val cs = cs.childScope()
    val component = componentFactory(cs, inlayVm).also {
      ClientProperty.put(it, COMPONENT_SCOPE_KEY, cs)
    }
    val inlay = inlaysManager.insertAfter(line, component) ?: return null
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      catch (e: Exception) {
        Disposer.dispose(inlay)
      }
    }
    return component
  }

  private fun getKey(line: Int, vm: VM): Any = line to vmKeyExtractor(vm)
}