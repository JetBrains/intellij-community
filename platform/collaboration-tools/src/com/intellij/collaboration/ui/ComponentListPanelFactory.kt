// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.async.ComputedListChange
import com.intellij.collaboration.async.changesFlow
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.COMPONENT_SCOPE_KEY
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UiImmediate
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
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

  fun <T : Any> createVertical(
    cs: CoroutineScope, model: ListModel<T>, gap: Int = 0,
    componentFactory: CoroutineScope.(T) -> JComponent,
  ): JPanel {
    val panel = VerticalListPanel(gap)
    cs.launchNow(Dispatchers.EdtImmediate) {
      val listener = object : ListDataListener {
        private fun addComponent(idx: Int, item: T) {
          val scope = childScope("Child component scope for $item")
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
      try {
        if (model.size > 0) {
          listener.intervalAdded(ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 0, model.size - 1))
        }
        awaitCancellation()
      }
      finally {
        model.removeListDataListener(listener)
      }
    }

    return panel
  }

  /**
   * @param T must implement proper equals/hashCode
   */
  fun <T : Any> createVertical(
    parentCs: CoroutineScope,
    items: StateFlow<List<T>>,
    panelInitializer: JPanel.() -> Unit = {},
    gap: Int = 0,
    componentFactory: CoroutineScope.(T) -> JComponent,
  ): JPanel {
    val panel = VerticalListPanel(gap).apply(panelInitializer)
    parentCs.launchNow(CoroutineName("List panel scope") + Dispatchers.EDT) {
      panel.showItems(items, componentFactory)
    }
    return panel
  }

  /**
   * @param T must implement proper equals/hashCode
   */
  fun <T : Any> createVertical(
    items: StateFlow<List<T>>,
    gap: Int = 0,
    componentFactory: CoroutineScope.(T) -> JComponent,
  ): JPanel {
    return VerticalListPanel(gap).apply {
      launchOnShow("List panel scope") {
        withContext(Dispatchers.UiImmediate) {
          showItems(items, componentFactory)
        }
      }
    }
  }

  /**
   * @param T must implement proper equals/hashCode
   */
  fun <T : Any> createHorizontal(
    parentCs: CoroutineScope,
    items: StateFlow<List<T>>,
    panelInitializer: JPanel.() -> Unit = {},
    gap: Int = 0,
    componentFactory: CoroutineScope.(T) -> JComponent,
  ): JPanel {
    val panel = HorizontalListPanel(gap).apply(panelInitializer)
    parentCs.launchNow(CoroutineName("List panel scope") + Dispatchers.EDT) {
      panel.showItems(items, componentFactory)
    }
    return panel
  }

  private suspend fun <T : Any> JPanel.showItems(items: StateFlow<List<T>>, componentFactory: CoroutineScope.(T) -> JComponent): Nothing {
    coroutineScope {
      val cs = this
      fun addComponent(idx: Int, item: T) {
        val scope = cs.childScope("Child component scope for $item")
        val component = scope.componentFactory(item).also {
          ClientProperty.put(it, COMPONENT_SCOPE_KEY, scope)
        }
        add(component, idx)
      }

      fun removeComponent(idx: Int) {
        val component = getComponent(idx)
        val componentCs = ClientProperty.get(component, COMPONENT_SCOPE_KEY)
        componentCs?.cancel()
        remove(idx)
      }

      items.changesFlow().collect { changes ->
        // apply changes. changesFlow should already order them in an applicable way
        for (change in changes) {
          when (change) {
            is ComputedListChange.Remove -> repeat(change.length) { removeComponent(change.atIndex) }
            is ComputedListChange.Insert -> change.values.forEachIndexed { i, v ->
              addComponent(change.atIndex + i, v)
            }
          }
        }

        if (changes.isNotEmpty()) {
          revalidate()
          repaint()
        }
      }
    }
    awaitCancellation()
  }
}