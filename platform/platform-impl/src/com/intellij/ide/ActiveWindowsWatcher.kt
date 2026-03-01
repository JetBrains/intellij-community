// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.ui.AppUIUtil.isInFullScreen
import com.intellij.ui.ComponentUtil
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window

@Internal
object ActiveWindowsWatcher {
  private val activatedWindows = LinkedHashSet<Window>()

  fun isTheCurrentWindowOnTheActivatedList(w: Window): Boolean {
    updateActivatedWindowSet()
    return activatedWindows.contains(w)
  }

  internal fun addActiveWindow(window: Window) {
    activatedWindows.add(window)
    updateActivatedWindowSet()
  }

  internal fun updateActivatedWindowSet() {
    activatedWindows.removeIf(::shouldRemoveFromActivatedSet)
    // The list can be empty if all windows are in fullscreen or minimized state
  }

  fun nextWindowAfter(w: Window): Window {
    return adjacentWindow(current = w, delta = 1, activatedWindows = activatedWindows)
  }

  fun nextWindowBefore(w: Window): Window {
    assert(activatedWindows.contains(w))
    return adjacentWindow(current = w, delta = -1, activatedWindows = activatedWindows)
  }
}

private fun shouldRemoveFromActivatedSet(window: Window): Boolean {
  return !window.isFocusableWindow ||
         !window.isVisible ||
         ComponentUtil.isMinimized(window) ||
         isInFullScreen(window) ||
         (window is Frame && window.isUndecorated) ||
         (window is Dialog && window.isUndecorated) ||
         UIUtil.isSimpleWindow(window)
}

private fun adjacentWindow(current: Window, delta: Int, activatedWindows: Collection<Window>): Window {
  val windows = getWindows(current, activatedWindows)
  val currentIndex = windows.indexOf(current)
  if (currentIndex < 0) {
    val direction = if (delta > 0) "after" else "before"
    throw IllegalArgumentException("The window $direction ${current.name} has not been found")
  }

  val targetIndex = (currentIndex + delta + windows.size) % windows.size
  return windows.get(targetIndex)
}


private fun getWindows(current: Window, activatedWindows: Collection<Window>): List<Window> {
  if (SystemInfoRt.isMac && SystemInfo.isJetBrainsJvm && activatedWindows.size > 1) {
    return activatedWindows
      .asSequence()
      .filter { it === current || isIncludedInWindowSwitchOrder(it) }
      .filter { it === current || Foundation.invoke(MacUtil.getWindowFromJavaWindow(it), "isOnActiveSpace").booleanValue() }
      .toList()
  }

  return activatedWindows.filter { it === current || isIncludedInWindowSwitchOrder(it) }
}


private fun isIncludedInWindowSwitchOrder(window: Window): Boolean {
  val project = getFrameHelper(window)?.getProject()
  if (project == null || project.isDisposed()) {
    return true
  }

  @Suppress("DEPRECATION") val capabilitiesService = ProjectFrameCapabilitiesService.getInstanceSync()
  return !capabilitiesService.has(project, ProjectFrameCapability.EXCLUDE_FROM_WINDOW_SWITCH_ORDER)
}
