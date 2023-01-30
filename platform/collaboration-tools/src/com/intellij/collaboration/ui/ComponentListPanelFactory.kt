// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.util.childScope
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

  // NOTE: new items are ALWAYS added to the end
  fun <T : Any> createVertical(parentCs: CoroutineScope,
                               items: Flow<List<T>>,
                               itemKeyExtractor: ((T) -> Any),
                               gap: Int = 0,
                               componentFactory: suspend (CoroutineScope, T) -> JComponent): JPanel {
    val cs = parentCs.childScope(Dispatchers.Main)
    val panel = VerticalListPanel(gap)
    val keyList = LinkedList<Any>()

    suspend fun addComponent(idx: Int, item: T) {
      withContext(Dispatchers.Main.immediate) {
        val scope = cs.childScope()
        val component = componentFactory(scope, item).also {
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
    }

    cs.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      items.collect { items ->
        val itemsByKey = items.associateBy(itemKeyExtractor)

        // remove missing
        val iter = keyList.iterator()
        var keyIdx = 0
        while (iter.hasNext()) {
          val key = iter.next()
          if (!itemsByKey.containsKey(key)) {
            iter.remove()
            removeComponent(keyIdx)
          }
          else {
            keyIdx++
          }
        }

        //add new
        val keySet = keyList.toMutableSet()
        for (item in items) {
          val key = itemKeyExtractor(item)
          if (keySet.contains(key)) continue

          val idx = keyList.size
          keyList.add(key)
          keySet.add(key)
          addComponent(idx, item)
        }

        withContext(Dispatchers.Main.immediate) {
          panel.revalidate()
          panel.repaint()
        }
      }
    }
    return panel
  }

  private val COMPONENT_SCOPE_KEY = Key.create<CoroutineScope>("ComponentListPanelFactory.Component.Scope")
}