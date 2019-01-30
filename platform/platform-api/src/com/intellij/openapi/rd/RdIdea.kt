// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.wm.IdeGlassPane
import com.jetbrains.rd.swing.awtMousePoint
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rdclient.util.idea.createNestedDisposable
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun IdeGlassPane.mouseMoved(): ISource<MouseEvent> {
  return object : ISource<MouseEvent> {
    override fun advise(lifetime: Lifetime, handler: (MouseEvent) -> Unit) {
      val listener = object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent?) {
          if (e != null) {
            handler(e)
          }
        }
      }

      val createNestedDisposable = lifetime.createNestedDisposable()

      this@mouseMoved.addMouseMotionPreprocessor(listener, createNestedDisposable)
      this@mouseMoved.addMouseMotionPreprocessor(listener, createNestedDisposable)
    }
  }
}

fun IdeGlassPane.childAtMouse(container: Container): ISource<Component?> = this@childAtMouse.mouseMoved()
  .map { SwingUtilities.convertPoint(it.component, it.x, it.y, container) }
  .map { container.getComponentAt(it) }


/*fun Container.childAtMouse(): IPropertyView<Component?> {
  return proxyProperty(null as Component?) { lifetime, set ->
    this@childAtMouse.mouseEntered().view(lifetime) { viewLT, in_ ->
      if (in_) {
        val gp = IdeGlassPaneUtil.find(this@childAtMouse) ?: return@view

        gp.childAtMouse(this@childAtMouse).advise(viewLT) {
          // println(it)
          set(it)
        }
      }
    }
  }
}*/

/**
 * TODO move to RdSwing
 */


/*fun JComponent.childAtMouse(): IPropertyView<Component?> {


  return proxyProperty(getChild(this@childAtMouse)) { lifetime, set ->
    val listener = object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent?) {
        println("mouseMoved")
        set(getChild(this@childAtMouse))
      }
    }
    lifetime.bracket(
      { this@childAtMouse.addMouseListener(listener) },
      { this@childAtMouse.removeMouseListener(listener) })



  }
}

private fun getChild(component: Component): Component? {
  val point: Point? = component.componentHoverPoint()
  point ?: return null
  return component.getComponentAt(point);
}*/



fun JComponent.childAtMouse(): IPropertyView<Component?> = this@childAtMouse.awtMousePoint()
  .map {
    if (it == null) null
    else {
      this@childAtMouse.getComponentAt(it)
    }
  }
