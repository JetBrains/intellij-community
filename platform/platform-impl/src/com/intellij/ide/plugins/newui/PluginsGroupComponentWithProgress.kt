// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.AsyncProcessIcon.BigCentered
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics

@ApiStatus.Internal
abstract class PluginsGroupComponentWithProgress @RequiresEdt constructor(eventHandler: EventHandler) : PluginsGroupComponent(eventHandler) {
  private var myLoadingIcon: AsyncProcessIcon? = BigCentered(IdeBundle.message("progress.text.loading"))
  private var myOnBecomingVisibleCallback: Runnable? = null

  init {
    myLoadingIcon!!.setOpaque(false)
    myLoadingIcon!!.setPaintPassiveIcon(false)
    add(myLoadingIcon)
    myLoadingIcon!!.resume()
  }

  override fun doLayout() {
    super.doLayout()
    updateIconLocation()
  }

  override fun paint(g: Graphics?) {
    super.paint(g)
    updateIconLocation()
  }

  private fun updateIconLocation() {
    if (myLoadingIcon != null && myLoadingIcon!!.isVisible()) {
      myLoadingIcon!!.updateLocation(this)
    }
  }

  fun showLoadingIcon() {
    LOG.debug("Marketplace tab: loading started")
    if (myLoadingIcon != null) {
      myLoadingIcon!!.setVisible(true)
      myLoadingIcon!!.resume()
      fullRepaint()
    }
  }

  fun hideLoadingIcon() {
    LOG.debug("Marketplace tab: loading stopped")
    if (myLoadingIcon != null) {
      myLoadingIcon!!.suspend()
      myLoadingIcon!!.setVisible(false)
      fullRepaint()
    }
  }

  private fun fullRepaint() {
    doLayout()
    revalidate()
    repaint()
  }

  fun dispose() {
    if (myLoadingIcon != null) {
      remove(myLoadingIcon)
      Disposer.dispose(myLoadingIcon!!)
      myLoadingIcon = null
    }
  }

  override fun clear() {
    super.clear()
    if (myLoadingIcon != null) {
      add(myLoadingIcon)
    }
  }

  fun setOnBecomingVisibleCallback(onVisibilityChangeCallbackOnce: Runnable) {
    myOnBecomingVisibleCallback = onVisibilityChangeCallbackOnce
  }

  override fun setVisible(visible: Boolean) {
    super.setVisible(visible)
    if (visible && myOnBecomingVisibleCallback != null) {
      val runnable = myOnBecomingVisibleCallback
      myOnBecomingVisibleCallback = null
      runnable!!.run()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(PluginsGroupComponentWithProgress::class.java)
  }
}