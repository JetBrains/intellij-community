// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar

import org.jetbrains.annotations.ApiStatus.Internal

sealed interface NavBarItemExpandResult

/**
 * ### Child popup
 *
 * When the current item has siblings, it's displayed in the popup with its siblings.
 * Selecting the item triggers the following:
 * - if [navigateOnClick] is `true`, the navigation to the current item will be attempted.
 * - otherwise, that the next popup will be shown with [children] in its content.
 * If there are no children, the navigation is attempted on the current item regardless of [navigateOnClick].
 *
 * ### Auto-expand
 *
 * When the current item is a single child itself,
 * it may be automatically "selected" in the popup, and the popup will be effectively skipped.
 * This is done in a loop, which ends when some item returns multiple children.
 *
 * Items may be navigatable, and we want to give the user an opportunity to navigate to the item,
 * even if the item is a single child and thus is about to be skipped.
 * The expanding loop will stop once it encounters an item with [navigateOnClick]
 * regardless whether it's a single child or not, which results in a popup with a single element.
 *
 * @param children children of the current item
 * @param navigateOnClick whether the current item should be navigatable when selected in the child popup
 */
fun NavBarItemExpandResult(children: List<NavBarVmItem>, navigateOnClick: Boolean): NavBarItemExpandResult {
  return NavBarItemExpandResultData(children, navigateOnClick)
}

@Internal
data class NavBarItemExpandResultData(
  val children: List<NavBarVmItem>,
  val navigateOnClick: Boolean,
) : NavBarItemExpandResult
