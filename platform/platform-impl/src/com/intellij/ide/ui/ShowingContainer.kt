// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.JInternalFrame

/**
 * This class allows emulating visibility (in the sense of [Component.isShowing]) for components without actually making them
 * visible. Just add the target component as a child of this class's instance to make the component 'showing'.
 */
@Internal
// We extend JInternalFrame to avoid processing of WHEN_IN_FOCUSED_WINDOW keybindings in attached components
class ShowingContainer : JInternalFrame() {
  init {
    // This is needed for components like OnePixelDivider, which only can be used in a frame/dialog with IdeGlassPane.
    setGlassPane(DummyGlassPane())
  }

  override fun isShowing(): Boolean {
    return true
  }

  fun isAttached(component: Component): Boolean {
    return component.parent === contentPane
  }

  companion object {
    private val CLIENT_PROPERTY = Key.create<ShowingContainer>("ShowingContainer")

    fun getInstance(project: Project?): ShowingContainer? {
      EDT.assertIsEdt()
      val frame = WindowManager.getInstance().getFrame(project) ?: return null
      val layeredPane = frame.layeredPane
      val sc = ClientProperty.get(layeredPane, CLIENT_PROPERTY)
      if (sc != null) {
        return sc
      }
      val container = ShowingContainer()
      ClientProperty.put(layeredPane, CLIENT_PROPERTY, container)
      layeredPane.add(container, -1 as Any /* layer number */)
      return container
    }
  }
}

private class DummyGlassPane : JComponent(), IdeGlassPane {
  override fun addMousePreprocessor(listener: MouseListener, parent: Disposable) {}
  override fun addMouseListener(listener: MouseListener, coroutineScope: CoroutineScope) {}
  override fun addMouseMotionPreprocessor(listener: MouseMotionListener, parent: Disposable) {}
  override fun addPainter(component: Component?, painter: Painter, parent: Disposable) {}
  override fun setCursor(cursor: Cursor?, requestor: Any) {}
}
