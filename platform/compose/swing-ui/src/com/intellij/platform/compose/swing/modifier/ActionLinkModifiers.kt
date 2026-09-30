// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.modifier

import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.Icon

/** @see com.intellij.ui.components.ActionLink.autoHideOnDisable */
@ApiStatus.Experimental
public fun SwingModifier.autoHideOnDisable(value: Boolean): SwingModifier =
  // The setter shows or hides the link from the value it is handed. Putting the value back runs that
  // again, so the link ends up as visible as the value says, not as visible as it was found.
  property(AutoHideOnDisableProperty, value, restores = RestorePolicy.DeclaredPropertyOnly)

/** @see com.intellij.ui.components.ActionLink.visited */
@ApiStatus.Experimental
public fun SwingModifier.visited(value: Boolean): SwingModifier = property(VisitedProperty, value)

/** @see com.intellij.ui.components.ActionLink.setLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.linkIcon(): SwingModifier = declaredIcon(ActionLinkIcon.Link)

/** @see com.intellij.ui.components.ActionLink.setContextHelpIcon */
@ApiStatus.Experimental
public fun SwingModifier.contextHelpIcon(): SwingModifier = declaredIcon(ActionLinkIcon.ContextHelp)

/** @see com.intellij.ui.components.ActionLink.setExternalLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.externalLinkIcon(): SwingModifier = declaredIcon(ActionLinkIcon.ExternalLink)

/** @see com.intellij.ui.components.ActionLink.setDropDownLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.dropDownLinkIcon(): SwingModifier = declaredIcon(ActionLinkIcon.DropDownLink)

/** @see com.intellij.ui.components.ActionLink.setIcon */
@ApiStatus.Experimental
public fun SwingModifier.actionLinkIcon(icon: Icon, atRight: Boolean): SwingModifier =
  declaredIcon(ActionLinkIcon.Custom(icon, atRight))

/**
 * Declares the link's icon. Every icon builder here, and the library's own `icon`, declares the `icon`
 * property, so they share a slot and the last one declared stands. The icon-text gap and the horizontal
 * text position are held beside it, because every setter here writes them too.
 */
private fun SwingModifier.declaredIcon(icon: ActionLinkIcon): SwingModifier =
  property(IconProperty, icon, alsoOverwrites = listOf(IconTextGapProperty, HorizontalTextPositionProperty))

private val AutoHideOnDisableProperty =
  ComponentPropertyDescriptor<ActionLink, Boolean>(
    name = "autoHideOnDisable",
    read = { it.autoHideOnDisable },
    write = { link, value -> link.autoHideOnDisable = value },
  )

private val VisitedProperty =
  ComponentPropertyDescriptor<ActionLink, Boolean>(
    name = "visited",
    read = { it.visited },
    write = { link, value -> link.visited = value },
  )

private val IconProperty =
  ComponentPropertyDescriptor<ActionLink, ActionLinkIcon>(
    name = "icon",
    read = { ActionLinkIcon.Held(it.icon) },
    write = { link, value -> value.writeTo(link) },
  )

private val IconTextGapProperty =
  ComponentPropertyDescriptor<ActionLink, Int>(
    name = "iconTextGap",
    read = { it.iconTextGap },
    write = { link, value -> link.iconTextGap = value },
  )

private val HorizontalTextPositionProperty =
  ComponentPropertyDescriptor<ActionLink, Int>(
    name = "horizontalTextPosition",
    read = { it.horizontalTextPosition },
    write = { link, value -> link.horizontalTextPosition = value },
  )

/** An icon a link is given, and the icon a link was found carrying. */
private sealed interface ActionLinkIcon {
  fun writeTo(link: ActionLink)

  data object Link : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.setLinkIcon()
    }
  }

  data object ContextHelp : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.setContextHelpIcon()
    }
  }

  data object ExternalLink : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.setExternalLinkIcon()
    }
  }

  data object DropDownLink : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.setDropDownLinkIcon()
    }
  }

  data class Custom(private val icon: Icon, private val atRight: Boolean) : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.setIcon(icon, atRight)
    }
  }

  data class Held(private val icon: Icon?) : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.icon = icon
    }
  }
}
