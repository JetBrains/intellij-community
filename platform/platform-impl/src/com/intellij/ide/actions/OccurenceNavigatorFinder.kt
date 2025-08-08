// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.event.ChangeListener
import kotlin.properties.Delegates.observable

@ApiStatus.Internal
class OccurenceNavigatorFinder private constructor(listener: (Boolean) -> Unit){
  companion object {
    @JvmStatic
    // IMPORTANT. The logic here should be kept in sync with the logic in 'doTrackNavigatorPresence'
    fun findNavigator(parent: JComponent): OccurenceNavigator? {
      val queue = LinkedList<JComponent>()
      queue.addLast(parent)
      while (!queue.isEmpty()) {
        when (val component = queue.removeFirst()) {
          is OccurenceNavigator -> return component
          is JTabbedPane -> {
            val selectedComponent = component.getSelectedComponent()
            if (selectedComponent is JComponent) {
              queue.addLast(selectedComponent)
            }
          }
          else -> {
            for (i in 0..<component.componentCount) {
              val child = component.getComponent(i)
              if (child is JComponent) {
                queue.addLast(child)
              }
            }
          }
        }
      }
      return null
    }

    fun trackNavigatorPresence(parent: JComponent, disposable: Disposable, listener: (Boolean) -> Unit) {
      OccurenceNavigatorFinder(listener).doTrackNavigatorPresence(parent, disposable)
    }
  }

  private var navigatorCount: Int by observable(0) { _, oldValue, newValue ->
    check(newValue >= 0) { "Unexpected count changes: $oldValue -> $newValue" }
    if (oldValue == 0 && newValue > 0) {
      listener(true)
    }
    else if (oldValue > 0 && newValue == 0) {
      listener(false)
    }
  }

  // IMPORTANT. The logic here should be kept in sync with the logic in 'findNavigator'
  private fun doTrackNavigatorPresence(component: JComponent, disposable: Disposable) {
    when (component) {
      is OccurenceNavigator -> {
        disposable.bracket({ navigatorCount++ }, { navigatorCount-- })
      }
      is JTabbedPane -> {
        var selectedDisposable: Disposable? = null
        fun trackSelected() {
          selectedDisposable?.let { Disposer.dispose(it) }
          val selected = component.getSelectedComponent()
          if (selected is JComponent) {
            selectedDisposable = Disposer.newDisposable(disposable)
            doTrackNavigatorPresence(selected, selectedDisposable)
          }
        }
        val changeListener = ChangeListener {
          trackSelected()
        }
        val propListener = PropertyChangeListener {
          trackSelected()
        }
        disposable.bracket({
                             trackSelected()
                             component.addChangeListener(changeListener)
                             component.addPropertyChangeListener("model", propListener)
                           },
                           {
                             component.removeChangeListener(changeListener)
                             component.removePropertyChangeListener("model", propListener)
                           })
      }
      else -> {
        val componentDisposables = IdentityHashMap<JComponent, Disposable>()
        fun trackChild(child: JComponent) {
          val childDisposable = Disposer.newDisposable(disposable)
          componentDisposables[child] = childDisposable
          doTrackNavigatorPresence(child, childDisposable)
        }
        fun trackInitialState() {
          for (i in 0..<component.componentCount) {
            val child = component.getComponent(i)
            if (child is JComponent) {
              trackChild(child)
            }
          }
        }
        val listener = object : ContainerListener {
          override fun componentAdded(e: ContainerEvent) {
            val child = e.child
            if (child is JComponent) {
              trackChild(child)
            }
          }

          override fun componentRemoved(e: ContainerEvent) {
            componentDisposables.remove(e.child)?.let { Disposer.dispose(it) }
          }
        }
        disposable.bracket({
                             trackInitialState()
                             component.addContainerListener(listener)
                           },
                           { component.removeContainerListener(listener) })
      }
    }
  }

  private fun Disposable.bracket(opening: () -> Unit, terminationAction: () -> Unit) {
    if (Disposer.tryRegister(this) { terminationAction() }) {
      opening()
    }
  }
}