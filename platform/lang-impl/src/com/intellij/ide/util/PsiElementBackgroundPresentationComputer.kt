// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.ScreenUtil
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.runWhenChanged
import com.intellij.util.ui.UIUtil.runWhenHidden
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.event.ListDataEvent

internal fun getComputer(list: JList<*>): PsiElementBackgroundPresentationComputer {
  val existing = UIUtil.getClientProperty(list, computerKey)
  if (existing != null) {
    return existing
  }
  val computer = PsiElementBackgroundPresentationComputer(list)
  UIUtil.putClientProperty(list, computerKey, computer)
  UIUtil.putClientProperty(list, ANIMATION_IN_RENDERER_ALLOWED, true)
  fun cleanUp() {
    UIUtil.putClientProperty(list, computerKey, null)
    UIUtil.putClientProperty(list, ANIMATION_IN_RENDERER_ALLOWED, null)
    computer.dispose()
  }
  runWhenHidden(list, ::cleanUp)
  runWhenChanged(list, "cellRenderer", ::cleanUp)
  return computer
}

private val computerKey = Key.create<PsiElementBackgroundPresentationComputer>("PsiElementBackgroundPresentationComputer")

private typealias RendererAndElement = Pair<PsiElementListCellRenderer<*>, PsiElement>

internal class PsiElementBackgroundPresentationComputer(list: JList<*>) {

  private val myCoroutineScope = CoroutineScope(Job())
  private val myRepaintQueue = myCoroutineScope.repaintQueue(list)
  private val myPresentationMap: MutableMap<RendererAndElement, Deferred<TargetPresentation>> = HashMap()

  fun dispose() {
    myCoroutineScope.cancel()
    myRepaintQueue.close()
    myPresentationMap.clear()
  }

  @RequiresEdt
  fun computePresentationAsync(renderer: PsiElementListCellRenderer<*>, element: PsiElement): Deferred<TargetPresentation> {
    return myPresentationMap.computeIfAbsent(RendererAndElement(renderer, element), ::doComputePresentationAsync)
  }

  private fun doComputePresentationAsync(rendererAndElement: RendererAndElement): Deferred<TargetPresentation> {
    val result = myCoroutineScope.async {
      //delay((Math.random() * 3000 + 2000).toLong()) // uncomment to add artificial delay to check out how it looks in UI
      readAction {
        val (renderer, element) = rendererAndElement
        renderer.computePresentation(element)
      }
    }
    myCoroutineScope.launch {
      result.join()
      myRepaintQueue.send(Unit) // repaint _after_ the resulting future is done
    }
    return result
  }
}

private fun CoroutineScope.repaintQueue(list: JList<*>): SendChannel<Unit> {
  val repaintQueue = Channel<Unit>(Channel.CONFLATED)
  launch(Dispatchers.EDT) {
    // A tick happens when an element has finished computing.
    // Several elements are also merged into a single tick because the Channel is CONFLATED.
    for (tick in repaintQueue) {
      notifyModelChanged(list)
      redrawListAndContainer(list)
      delay(100) // update UI no more often that once in 100ms
    }
  }
  return repaintQueue
}

/**
 * This method forces [javax.swing.plaf.basic.BasicListUI.updateLayoutStateNeeded] to become non-zero via the path:
 * [JList.getModel]
 * -> [AbstractListModel.getListDataListeners]
 * -> [javax.swing.event.ListDataListener.contentsChanged]
 * -> [javax.swing.plaf.basic.BasicListUI.Handler.contentsChanged]
 *
 * It's needed so next call of [javax.swing.plaf.basic.BasicListUI.maybeUpdateLayoutState] will recompute list's preferred size.
 */
private fun notifyModelChanged(list: JList<*>) {
  val model = list.model
  if (model !is AbstractListModel) {
    error("Unsupported list model: " + model.javaClass.name)
  }
  val size = model.size
  if (size == 0) {
    return
  }
  val listeners = model.listDataListeners
  if (listeners.isEmpty()) {
    return
  }
  val e = ListDataEvent(list, ListDataEvent.CONTENTS_CHANGED, 0, size - 1)
  for (listener in listeners) {
    listener.contentsChanged(e)
  }
}

private fun redrawListAndContainer(list: JList<*>) {
  if (!list.isShowing) {
    return
  }
  resizePopup(list)
  list.repaint()
}

private fun resizePopup(list: JList<*>) {
  val popup = PopupUtil.getPopupContainerFor(list) ?: return
  if (popup is AbstractPopup && popup.dimensionServiceKey != null) {
    return
  }
  val popupLocation = popup.locationOnScreen
  val rectangle = Rectangle(popupLocation, list.parent.preferredSize)
  ScreenUtil.fitToScreen(rectangle)
  if (rectangle.width > popup.size.width) { // don't shrink popup
    popup.setLocation(Point(rectangle.x, popupLocation.y)) // // don't change popup vertical position on screen
    popup.size = Dimension(rectangle.width, popup.size.height) // don't change popup height
  }
}
