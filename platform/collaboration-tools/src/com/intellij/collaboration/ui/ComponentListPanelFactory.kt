// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.COMPONENT_SCOPE_KEY
import com.intellij.ui.ClientProperty
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

object ComponentListPanelFactory {
  fun <T : Any> createVertical(model: ListModel<T>, gap: Int = 0, componentFactory: (T) -> JComponent): JPanel {
    val panel = VerticalListPanel(gap)

    model.addListDataListener(object : ListDataListener {
      override fun intervalRemoved(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          panel.remove(i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        for (i in e.index0..e.index1) {
          panel.add(componentFactory(model.getElementAt(i)), i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        for (i in e.index1 downTo e.index0) {
          panel.remove(i)
        }
        for (i in e.index0..e.index1) {
          panel.add(componentFactory(model.getElementAt(i)), i)
        }
        panel.validate()
        panel.repaint()
      }
    })

    for (item in model.items) {
      panel.add(componentFactory(item))
    }

    return panel
  }

  /**
   * [items] must not have duplicates by key [itemKeyExtractor]
   */
  fun <T : Any> createVertical(parentCs: CoroutineScope,
                               items: Flow<List<T>>,
                               itemKeyExtractor: ((T) -> Any),
                               panelInitializer: JPanel.() -> Unit = {},
                               gap: Int = 0,
                               componentFactory: (CoroutineScope, T) -> JComponent): JPanel {
    val cs = parentCs.childScope(Dispatchers.Main)
    val panel = VerticalListPanel(gap).apply(panelInitializer)

    fun addComponent(idx: Int, ignored: Any, item: T) {
      val componentCs = cs.childScope()
      val component = componentFactory(componentCs, item).also {
        ClientProperty.put(it, COMPONENT_SCOPE_KEY, componentCs)
      }
      panel.add(component, idx)
    }

    suspend fun removeComponent(idx: Int) {
      val component = panel.getComponent(idx)
      val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
      cs.launch {
        componentCs?.coroutineContext?.get(Job)?.cancelAndJoin()
      }
      panel.remove(idx)
    }

    fun moveComponent(oldIdx: Int, newIdx: Int) {
      val component = panel.getComponent(oldIdx)
      panel.remove(oldIdx)
      panel.add(component, newIdx)
    }

    trackChanges(cs, itemKeyExtractor, items, { panel.removeAll() }, ::addComponent, ::removeComponent, ::moveComponent,
                 { panel.revalidate(); panel.repaint() })

    return panel
  }

  fun <T : Any> trackChanges(cs: CoroutineScope,
                             itemKeyExtractor: (T) -> Any,
                             itemsFlow: Flow<List<T>>,
                             removeAll: suspend () -> Unit,
                             addComponent: suspend (idx: Int, key: Any, item: T) -> Unit,
                             removeComponent: suspend (idx: Int) -> Unit,
                             moveComponent: suspend (oldIdx: Int, newIdx: Int) -> Unit,
                             revalidateAndRepaint: suspend () -> Unit) {
    val keyList = LinkedList<Any>()

    suspend fun trackedAddComponent(idx: Int, key: Any, item: T) {
      withContext(Dispatchers.Main.immediate) {
        keyList.add(idx, key)
        addComponent(idx, key, item)
      }
    }

    suspend fun trackedRemoveComponent(idx: Int) {
      withContext(Dispatchers.Main.immediate) {
        removeComponent(idx)
        keyList.removeAt(idx)
      }
    }

    suspend fun trackedMoveComponent(oldIdx: Int, newIdx: Int) {
      withContext(Dispatchers.Main.immediate) {
        val key = keyList.removeAt(oldIdx)
        moveComponent(oldIdx, newIdx)
        keyList.add(newIdx, key)
      }
    }

    cs.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      var firstCollect = true
      itemsFlow.conflate().collect { mutableItems ->
        val items = mutableItems.toList()
        var revalidate = firstCollect
        if (firstCollect) {
          withContext(Dispatchers.Main.immediate) {
            removeAll()
          }
          firstCollect = false
        }
        // remove missing
        val itemsByKey = items.associateBy(itemKeyExtractor)
        // remove missing
        var currentIdx = 0
        while (currentIdx < keyList.size) {
          val key = keyList[currentIdx]
          if (!itemsByKey.containsKey(key)) {
            trackedRemoveComponent(currentIdx)
            revalidate = true
          }
          else {
            currentIdx++
          }
        }

        // move or add new
        for ((idx, item) in items.withIndex()) {
          val key = itemKeyExtractor(item)
          if (idx > keyList.size - 1) {
            trackedAddComponent(idx, key, item)
            revalidate = true
            continue
          }

          if (keyList[idx] != key) {
            var existingIdx = -1
            for (i in idx until keyList.size) {
              if (keyList[i] == key) {
                existingIdx = i
                break
              }
            }
            if (existingIdx > 0) {
              trackedMoveComponent(existingIdx, idx)
              revalidate = true
            }
            else {
              trackedAddComponent(idx, key, item)
              revalidate = true
            }
          }
        }

        if (revalidate) {
          withContext(Dispatchers.Main.immediate) {
            revalidateAndRepaint()
          }
        }
      }
    }
  }
}