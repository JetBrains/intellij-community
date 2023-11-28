// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.COMPONENT_SCOPE_KEY
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.containers.toArray
import com.intellij.util.diff.Diff
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
   * @param T must implement proper equals/hashCode
   */
  fun <T : Any> createVertical(parentCs: CoroutineScope,
                               items: Flow<List<T>>,
                               panelInitializer: JPanel.() -> Unit = {},
                               gap: Int = 0,
                               componentFactory: CoroutineScope.(T) -> JComponent): JPanel {
    val cs = parentCs.childScope(Dispatchers.Main)
    val panel = VerticalListPanel(gap).apply(panelInitializer)
    val currentList = LinkedList<T>()

    fun addComponent(idx: Int, item: T) {
      currentList.add(idx, item)
      val scope = cs.childScope()
      val component = scope.componentFactory(item).also {
        ClientProperty.put(it, COMPONENT_SCOPE_KEY, scope)
      }
      panel.add(component, idx)
    }

    fun removeComponent(idx: Int) {
      val component = panel.getComponent(idx)
      val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
      componentCs?.cancel()
      panel.remove(idx)
      currentList.removeAt(idx)
    }

    cs.launchNow {
      var firstCollect = true
      items.collect { items ->
        var revalidate = firstCollect
        if (firstCollect) {
          panel.removeAll()
          firstCollect = false
        }

        if (items.isEmpty()) {
          for (i in currentList.indices) {
            removeComponent(i)
          }
          revalidate = true
        }
        else if (currentList.isEmpty()) {
          for ((i, item) in items.withIndex()) {
            addComponent(i, item)
          }
          revalidate = true
        }
        else {
          val changes = withContext(Dispatchers.Default) {
            buildList {
              var change = Diff.buildChanges(currentList.toArray(), items.toArray(emptyArray<Any>()))
              while (change != null) {
                add(change)
                change = change.link
              }
            }
          }

          for (change in changes.asReversed()) {
            if (change.deleted > 0) {
              for (i in change.line0 + change.deleted - 1 downTo change.line0) {
                removeComponent(i)
                revalidate = true
              }
            }
          }

          for (change in changes) {
            if (change.inserted > 0) {
              for (i in change.line1 until change.line1 + change.inserted) {
                addComponent(i, items[i])
                revalidate = true
              }
            }
          }
        }

        if (revalidate) {
          panel.revalidate()
          panel.repaint()
        }
      }
    }
    return panel
  }
}