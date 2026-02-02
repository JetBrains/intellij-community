// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.wayland

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.ui.tabs.JBTabsEx
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle
import java.awt.Window
import javax.swing.SwingUtilities

/**
 * Returns the area where the given popup is allowed to be located.
 *
 * Normally the environment will allow the popup to be shown
 * only if at least one pixel of it is located within this area.
 *
 * For this function to return a non-`null` meaningful value,
 * the popup must be showing, it must have a parent,
 * and it must be a descendant of some top-level (as in "not a popup") window.
 *
 * @return the rectangle in the given component's parent coordinate system, or `null` if the area cannot be determined
 */
@ApiStatus.Internal
fun getValidBoundsForPopup(popup: Component): Rectangle? {
  if (!popup.isShowing) {
    LOG.warn("Impossible to determine the valid bounds because the popup is not showing: $popup")
    return null
  }
  val validBounds = getNearestTopLevelParentBounds(popup) ?: return null
  LOG.debug { "The allowed bounds in screen coordinates are $validBounds" }
  val directParent = popup.parent
  if (directParent == null) {
    LOG.warn("Impossible to determine the valid bounds because the popup has no direct parent: $popup")
    return null
  }
  // Now convert the allowed bounds to the direct parent's coordinate system.
  val directParentLocation = directParent.locationOnScreen
  validBounds.x -= directParentLocation.x
  validBounds.y -= directParentLocation.y
  LOG.debug { "The allowed bounds in parent coordinates are $validBounds" }
  return validBounds
}

@ApiStatus.Internal
fun moveToFitChildPopupX(childBounds: Rectangle, parent: Component) {
  if (!parent.isShowing) {
    LOG.warn("Impossible to fit the child popup to the main window because the parent is not showing: $parent")
    return
  }

  val childLocation = childBounds.location
  LOG.debug { "The initial child bounds are $childBounds" }
  SwingUtilities.convertPointToScreen(childLocation, parent)
  LOG.debug { "The initial child location relative to the screen is $childLocation" }

  val topLevelBounds = getNearestTopLevelParentBounds(parent) ?: return

  LOG.debug { "The relative parent location is ${parent.location}" }
  val parentBounds = Rectangle(parent.locationOnScreen, parent.size)
  LOG.debug { "The screen parent bounds are $parentBounds" }

  childLocation.x = fitValue(
    location = childLocation.x,
    width = childBounds.width,
    start1 = topLevelBounds.x,
    end1 = parentBounds.x,
    start2 = parentBounds.x + parentBounds.width,
    end2 = topLevelBounds.x + topLevelBounds.width,
    preferLess = childLocation.x < parentBounds.x + parentBounds.width / 2,
  )

  SwingUtilities.convertPointFromScreen(childLocation, parent)
  childBounds.location = childLocation
  LOG.debug { "The final result is $childBounds" }
}

private fun getNearestTopLevelParentBounds(component: Component): Rectangle? {
  // Can't use ComponentUtil.findUltimateParent() because we need the nearest non-popup window,
  // as it's what Wayland considers to be the owner of the popup.
  val topLevelWindow = getNearestTopLevelAncestor(component)
  if (topLevelWindow !is Window) { // pretty much a non-null check with a smart cast
    LOG.warn("The top level parent isn't a window, but $topLevelWindow")
    return null
  }
  val topLevelBounds = Rectangle(topLevelWindow.locationOnScreen, topLevelWindow.size)
  LOG.debug { "The top level bounds are $topLevelBounds" }
  return topLevelBounds
}

/**
 * Returns the nearest top-level ancestor window of the given component.
 *
 * Here, "top-level" means "not a popup as far as Wayland is concerned."
 *
 * @return the nearest top-level window or `null` if there's none
 */
@ApiStatus.Internal
fun getNearestTopLevelAncestor(component: Component): Component? {
  return ComponentUtil.findParentByCondition(component) { c ->
    c is Window && c.type != Window.Type.POPUP
  }
}

/**
 * Slightly decreases the screen height on Wayland.
 *
 * Because Wayland doesn't report the actual screen insets,
 * for some use cases, like trying to determine the maximum height of some menu or popup,
 * the maximum size can be larger than the actual available screen space.
 * This function applies a workaround for this problem by subtracting some fixed
 * amount of space from the screen height.
 *
 * Does nothing if not running under the native Wayland toolkit.
 */
@ApiStatus.Internal
fun addFakeScreenInsets(rectangle: Rectangle) {
  if (!StartupUiUtil.isWaylandToolkit()) return
  val total = JBUI.scale(Registry.intValue("wayland.screen.vInsets", defaultValue = 50, minValue = 0, maxValue = 300))
  val top = total / 2
  rectangle.y += top
  rectangle.height -= total
}

/**
 * Tries to estimate the available screen height.
 * 
 * Since the screen size on Wayland can't be trusted (JBR-9884),
 * the size of the topmost window owner is used as an estimate.
 * If it's too small, then `null` is returned and the caller is expected
 * to provide a sensible fallback.
 */
@ApiStatus.Internal
fun getFakeScreenHeight(component: Component?): Int? {
  if (component == null) return null
  val parent = ComponentUtil.findUltimateParent(component)
  // Check for IdeFrameImpl here because other windows can be too small for this calculation to have any meaning.
  // The ultimate parent should be an IdeFrameImpl anyway.
  if (parent !is Window || !parent.isShowing) return null
  val screenHeight = parent.getHeight()
  // Check if the main window is too small.
  // This is a reasonable fallback, as even a tiny 14" MacBook screen with the maximum scaling is 665 px, so 600 should be safe.
  if (screenHeight < 600) return null
  return screenHeight
}

@ApiStatus.Internal
fun isAllowedTabDnD(tabs: JBTabsEx): Boolean {
  if (!StartupUiUtil.isWaylandToolkit() || !tabs.isEditorTabs) {
    return true
  }

  val window = SwingUtilities.getWindowAncestor(tabs.getComponent()) ?: return true
  return window !is IdeFrame.Child
}

private fun fitValue(location: Int, width: Int, start1: Int, end1: Int, start2: Int, end2: Int, preferLess: Boolean): Int {
  LOG.debug { "The available intervals are $start1..$end1 and $start2..$end2, the popup size is $width" }
  if (location >= start1 && location + width < end1) {
    LOG.debug { "The initial location already fits within the first interval" }
    return location
  }
  if (location >= start2 && location + width < end2) {
    LOG.debug { "The initial location already fits within the second interval" }
    return location
  }
  val space1 = end1 - start1
  val space2 = end2 - start2
  if (space1 >= width && space2 >= width) {
    LOG.debug { "We have enough space on both sides, preferring the ${if (preferLess) "first" else "second"}" }
    return if (preferLess) end1 - width else start2
  }
  else if (space1 >= width) {
    val result = end1 - width
    LOG.debug { "We have enough space the first side: $space1 >= $width, the result is $end1-$width=$result" }
    return result
  }
  else if (space2 >= width) {
    LOG.debug { "We have enough space the first side: $space2 >= $width, the result is $start2" }
    return start2
  }
  else if (space1 > 0 && space1 > space2) {
    LOG.debug { "We have more space the first side: $space1 > $space2, the result is $start1" }
    return start1
  }
  else if (space2 > 0) {
    val result = end2 - width
    LOG.debug { "We have more space the second side: $space1 <= $space2, the result is $end2-$width=$result" }
    return result
  }
  else {
    LOG.debug { "We don't have any space at all, falling back to the initial value" }
    return location
  }
}

private val LOG = fileLogger()
