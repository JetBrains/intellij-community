// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ArrayUtil
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JScrollBar
import javax.swing.JScrollPane

/**
 * A state of some scroll pane, denoting the current positions of the scrollbars.
 */
@Internal
data class ScrollPaneScrolledState(
  val scrollPane: JScrollPane,
  val state: ScrolledState,
)

@Internal
data class ScrolledState(
  val isHorizontalAtStart: Boolean,
  val isHorizontalAtEnd: Boolean,
  val isVerticalAtStart: Boolean,
  val isVerticalAtEnd: Boolean,
)

/**
 * A class that tracks scroll panes in a container and provides their scroll states.
 *
 * It watches all scroll panes located inside a given component, tracking them as they're added or removed,
 * as long as they match the given filter.
 *
 * The given callback is invoked when one of the following events occurs:
 * - a scroll pane already exists in the container when the tracker is created
 * (invoked once for every scroll pane);
 * - a new scroll pane is added;
 * - an existing scroll pane is moved, shown or resized;
 * - an existing scroll pane changed its state, that is, at least one of the booleans in [ScrollPaneScrolledState]
 * has changed.
 *
 * @param container The container that contains the scroll panes to be tracked.
 * @param filter A predicate function that determines whether a scroll pane should be tracked or not.
 * @param callback The callback function to be invoked whenever a scroll pane's state changes.
 */
@Internal
class ScrollPaneTracker(
  container: Component,
  private val filter: (JScrollPane) -> Boolean,
  private val callback: (ScrollPaneTracker) -> Unit,
) {

  val scrollPaneStates: List<ScrollPaneScrolledState>
    get() = myTrackers.map { it.state }

  private val myTrackers = SmartList<ScrollPaneScrolledStateTracker>()

  private val myWatcher = object : ComponentTreeWatcher(ArrayUtil.EMPTY_CLASS_ARRAY) {
    override fun processComponent(component: Component) {
      if (component is JScrollPane && filter(component)) {
        registerScrollPane(component)
      }
    }

    override fun unprocessComponent(component: Component?) {
      if (component is JScrollPane) {
        unregisterScrollPane(component)
      }
    }
  }

  init {
    myWatcher.register(container)
  }

  private fun registerScrollPane(scrollPane: JScrollPane) {
    myTrackers.add(ScrollPaneScrolledStateTracker(scrollPane) {
      callback(this)
    })
    callback(this)
  }

  private fun unregisterScrollPane(scrollPane: JScrollPane) {
    val iterator: MutableIterator<ScrollPaneScrolledStateTracker> = myTrackers.iterator()
    while (iterator.hasNext()) {
      val trackers = iterator.next()
      if (trackers.scrollPane == scrollPane) {
        trackers.detach()
        iterator.remove()
        break
      }
    }
  }

}

/**
 * A state tracker for monitoring the scrolling state of a JScrollPane.
 *
 * The given callback is invoked every time the actual state changes,
 * that is, when a scrollbar hits or leaves either end, and additionally whenever
 * the scroll pane is shown, moved or resizes, regardless of state changes.
 *
 * @property scrollPane The scroll pane to track.
 * @property callback The callback function to be invoked when the scrolling state changes.
 */
@Internal
class ScrollPaneScrolledStateTracker(val scrollPane: JScrollPane, private val callback: ((ScrollPaneScrolledState) -> Unit)) {

  private val boundsListener = BoundsListener()
  private val horizontalListener = ScrollBarValueListener()
  private val verticalListener = ScrollBarValueListener()

  var state: ScrollPaneScrolledState = ScrollPaneScrolledState(
    scrollPane,
    ScrolledState(
      isHorizontalAtStart = true,
      isHorizontalAtEnd = true,
      isVerticalAtStart = true,
      isVerticalAtEnd = true,
    )
  ); private set

  private val scrollBarListener = PropertyChangeListener { e ->
    when (e.propertyName) {
      "horizontalScrollBar" -> {
        (e.oldValue as? JScrollBar?)?.removeAdjustmentListener(horizontalListener)
        (e.newValue as? JScrollBar?)?.addAdjustmentListener(horizontalListener)
      }
      "verticalScrollBar" -> {
        (e.oldValue as? JScrollBar?)?.removeAdjustmentListener(verticalListener)
        (e.newValue as? JScrollBar?)?.addAdjustmentListener(verticalListener)
      }
    }
  }

  init {
    scrollPane.addComponentListener(boundsListener)
    scrollPane.horizontalScrollBar?.addAdjustmentListener(horizontalListener)
    scrollPane.verticalScrollBar?.addAdjustmentListener(verticalListener)
    scrollPane.addPropertyChangeListener(scrollBarListener)
  }

  fun detach() {
    scrollPane.removePropertyChangeListener(scrollBarListener)
    scrollPane.horizontalScrollBar?.removeAdjustmentListener(horizontalListener)
    scrollPane.verticalScrollBar?.removeAdjustmentListener(verticalListener)
    scrollPane.removeComponentListener(boundsListener)
  }

  private fun fireCallback(fireAnyway: Boolean) {
    val newState = ScrollPaneScrolledState(
      scrollPane,
      ScrolledState(
        isHorizontalAtStart = horizontalListener.isAtStart,
        isHorizontalAtEnd = horizontalListener.isAtEnd,
        isVerticalAtStart = verticalListener.isAtStart,
        isVerticalAtEnd = verticalListener.isAtEnd,
      )
    )
    if (fireAnyway || newState != state) {
      state = newState
      callback.invoke(newState)
    }
  }

  private inner class ScrollBarValueListener : AdjustmentListener {

    var isAtStart = true
    var isAtEnd = true

    override fun adjustmentValueChanged(e: AdjustmentEvent) {
      isAtStart = e.value == 0
      isAtEnd = e.value == e.adjustable.run { maximum - visibleAmount }
      fireCallback(fireAnyway = false)
    }
  }

  private inner class BoundsListener : ComponentAdapter() {
    override fun componentShown(e: ComponentEvent?) {
      fireCallback(fireAnyway = true)
    }

    override fun componentMoved(e: ComponentEvent?) {
      fireCallback(fireAnyway = true)
    }

    override fun componentResized(e: ComponentEvent?) {
      fireCallback(fireAnyway = true)
    }
  }

}
