// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle
import javax.swing.*

@ApiStatus.Internal
class DeferredIconRepaintScheduler {
  private val repaintScheduler = RepaintScheduler()

  @RequiresEdt
  fun createRepaintRequest(component: Component?, x: Int, y: Int): RepaintRequest {
    val target = getTarget(component)
    val paintingParent = SwingUtilities.getAncestorOfClass(PaintingParent::class.java, component)
    val paintingParentRec = if (paintingParent == null) null else (paintingParent as PaintingParent).getChildRec(component!!)
    return RepaintRequest(component = component,
                          x = x,
                          y = y,
                          target = target,
                          paintingParent = paintingParent,
                          paintingParentRec = paintingParentRec)
  }

  @RequiresEdt
  fun scheduleRepaint(request: RepaintRequest, iconWidth: Int, iconHeight: Int, alwaysSchedule: Boolean) {
    val actualTarget = request.getActualTarget()
    if (actualTarget == null) {
      return
    }
    val component = request.component
    if (!alwaysSchedule && component == actualTarget) {
      component.repaint(request.x, request.y, iconWidth, iconHeight)
    }
    else {
      repaintScheduler.pushDirtyComponent(actualTarget, request.paintingParentRec)
    }
  }

  private fun getTarget(c: Component?): Component? {
    val list = SwingUtilities.getAncestorOfClass(JList::class.java, c)
    if (list != null) {
      return list
    }

    // check table first to process com.intellij.ui.treeStructure.treetable.TreeTable correctly
    val table = SwingUtilities.getAncestorOfClass(JTable::class.java, c)
    if (table != null) {
      return table
    }

    val tree = SwingUtilities.getAncestorOfClass(JTree::class.java, c)
    if (tree != null) {
      return tree
    }

    val box = SwingUtilities.getAncestorOfClass(JComboBox::class.java, c)
    if (box != null) {
      return box
    }

    val tabLabel = SwingUtilities.getAncestorOfClass(TabLabel::class.java, c)
    if (tabLabel != null) {
      return tabLabel
    }

    return c
  }

  data class RepaintRequest(
    @JvmField internal val component: Component?,
    @JvmField internal val x: Int,
    @JvmField internal val y: Int,
    @JvmField internal val target: Component?,
    private val paintingParent: Component?,
    @JvmField internal val paintingParentRec: Rectangle?
  ) {
    fun getActualTarget(): Component? {
      if (target == null) {
        return null
      }
      if (SwingUtilities.getWindowAncestor(target) != null) {
        return target
      }

      if (paintingParent != null && SwingUtilities.getWindowAncestor(paintingParent) != null) {
        return paintingParent
      }
      return null
    }
  }

  private data class RepaintSchedulerRequest(@JvmField val component: Component, @JvmField val rectangle: Rectangle?)

  private class RepaintScheduler {
    private val alarm = Alarm()
    private val queue = LinkedHashSet<RepaintSchedulerRequest>()

    fun pushDirtyComponent(c: Component, rec: Rectangle?) {
      // assert myQueue accessed from EDT only
      ApplicationManager.getApplication().assertIsDispatchThread()
      alarm.cancelAllRequests()
      alarm.addRequest({
        for (each in queue) {
          val r = each.rectangle
          if (r == null) {
            each.component.repaint()
          }
          else {
            each.component.repaint(r.x, r.y, r.width, r.height)
          }
        }
        queue.clear()
      }, 50)
      queue.add(RepaintSchedulerRequest(c, rec))
    }
  }
}