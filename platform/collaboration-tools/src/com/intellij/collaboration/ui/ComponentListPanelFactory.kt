// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.COMPONENT_SCOPE_KEY
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
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
        if (e.index0 < 0 || e.index1 < 0) return
        for (i in e.index1 downTo e.index0) {
          panel.remove(i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun intervalAdded(e: ListDataEvent) {
        if (e.index0 < 0 || e.index1 < 0) return
        for (i in e.index0..e.index1) {
          panel.add(componentFactory(model.getElementAt(i)), i)
        }
        panel.revalidate()
        panel.repaint()
      }

      override fun contentsChanged(e: ListDataEvent) {
        if (e.index0 < 0 || e.index1 < 0) return
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

  fun <T : Any> createVertical(cs: CoroutineScope, model: ListModel<T>, gap: Int = 0,
                               componentFactory: CoroutineScope.(T) -> JComponent): JPanel {
    val panel = VerticalListPanel(gap)
    cs.launchNow(Dispatchers.Main.immediate) {
      val listener = object : ListDataListener {
        private fun addComponent(idx: Int, item: T) {
          val scope = childScope()
          val component = scope.componentFactory(item).also {
            ClientProperty.put(it, COMPONENT_SCOPE_KEY, scope)
          }
          panel.add(component, idx)
        }

        private fun removeComponent(idx: Int) {
          val component = panel.getComponent(idx)
          val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
          componentCs?.coroutineContext?.get(Job)?.cancel()
          panel.remove(idx)
        }

        override fun intervalRemoved(e: ListDataEvent) {
          if (e.index0 < 0 || e.index1 < 0) return
          for (i in e.index1 downTo e.index0) {
            removeComponent(i)
          }
          panel.revalidate()
          panel.repaint()
        }

        override fun intervalAdded(e: ListDataEvent) {
          if (e.index0 < 0 || e.index1 < 0) return
          for (i in e.index0..e.index1) {
            addComponent(i, model.getElementAt(i))
          }
          panel.revalidate()
          panel.repaint()
        }

        override fun contentsChanged(e: ListDataEvent) {
          if (e.index0 < 0 || e.index1 < 0) return
          for (i in e.index1 downTo e.index0) {
            removeComponent(i)
          }
          for (i in e.index0..e.index1) {
            addComponent(i, model.getElementAt(i))
          }
          panel.validate()
          panel.repaint()
        }
      }
      model.addListDataListener(listener)

      if (model.size > 0) {
        listener.intervalAdded(ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 0, model.size - 1))
      }
      awaitCancellationAndInvoke {
        model.removeListDataListener(listener)
      }
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
                               componentFactory: CoroutineScope.(T) -> JComponent): JPanel {
    val cs = parentCs.childScope(Dispatchers.Main)
    val panel = VerticalListPanel(gap).apply(panelInitializer)
    val keyList = LinkedList<Any>()

    suspend fun addComponent(idx: Int, key: Any, item: T) {
      keyList.add(idx, key)
      withContext(Dispatchers.Main.immediate) {
        val scope = cs.childScope()
        val component = scope.componentFactory(item).also {
          ClientProperty.put(it, COMPONENT_SCOPE_KEY, scope)
        }
        panel.add(component, idx)
      }
    }

    suspend fun removeComponent(idx: Int) {
      withContext(Dispatchers.Main.immediate) {
        val component = panel.getComponent(idx)
        val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
        cs.launch {
          componentCs?.coroutineContext?.get(Job)?.cancelAndJoin()
        }
        panel.remove(idx)
      }
      keyList.removeAt(idx)
    }

    suspend fun moveComponent(oldIdx: Int, newIdx: Int) {
      val key = keyList.removeAt(oldIdx)
      withContext(Dispatchers.Main.immediate) {
        val component = panel.getComponent(oldIdx)
        panel.remove(oldIdx)
        panel.add(component, newIdx)
      }
      keyList.add(newIdx, key)
    }

    cs.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      var firstCollect = true
      items.collect { items ->
        var revalidate = firstCollect
        if (firstCollect) {
          withContext(Dispatchers.Main.immediate) {
            panel.removeAll()
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
            removeComponent(currentIdx)
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
            addComponent(idx, key, item)
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
              moveComponent(existingIdx, idx)
              revalidate = true
            }
            else {
              addComponent(idx, key, item)
              revalidate = true
            }
          }
        }

        if (revalidate) {
          withContext(Dispatchers.Main.immediate) {
            panel.revalidate()
            panel.repaint()
          }
        }
      }
    }
    return panel
  }
}