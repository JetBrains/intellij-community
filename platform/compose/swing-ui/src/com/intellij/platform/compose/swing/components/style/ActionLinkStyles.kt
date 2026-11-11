// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components.style

import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.Icon

/**
 * What a style can set on an IDE link: the button properties, and the link's own icon, visited state and
 * hiding on disable.
 *
 * @see com.intellij.ui.components.ActionLink
 */
@ApiStatus.Experimental
public interface ActionLinkStyleScope : ButtonStyleScope

/**
 * Styles for every `ActionLink`, `BrowserLink` and `DropDownLink` below the provider. A link does not apply
 * `Buttons`, so a style for push buttons leaves the links alone.
 */
@ApiStatus.Experimental
public val ActionLinks: NestedStyleKey<ActionLinkStyleScope> = NestedStyleKey("ActionLinks")

internal val ActionLinkKind: SwingStyleKind<ActionLinkStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : ActionLinkStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(ActionLinks)
  }

/** @see com.intellij.ui.components.ActionLink.autoHideOnDisable */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.autoHideOnDisable(value: Boolean): Unit = AutoHideOnDisableProperty.provide(value)

/** @see com.intellij.ui.components.ActionLink.visited */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.visited(value: Boolean): Unit = VisitedProperty.provide(value)

/** @see com.intellij.ui.components.ActionLink.setLinkIcon */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.linkIcon(): Unit = IconProperty.provide(ActionLinkIcon.Link)

/** @see com.intellij.ui.components.ActionLink.setContextHelpIcon */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.contextHelpIcon(): Unit = IconProperty.provide(ActionLinkIcon.ContextHelp)

/** @see com.intellij.ui.components.ActionLink.setExternalLinkIcon */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.externalLinkIcon(): Unit = IconProperty.provide(ActionLinkIcon.ExternalLink)

/** @see com.intellij.ui.components.ActionLink.setDropDownLinkIcon */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.dropDownLinkIcon(): Unit = IconProperty.provide(ActionLinkIcon.DropDownLink)

/**
 * Sets [icon] beside the link's text, on the right when [atRight] is `true`.
 *
 * @see com.intellij.ui.components.ActionLink.setIcon
 */
@ApiStatus.Experimental
public fun ActionLinkStyleScope.actionLinkIcon(icon: Icon, atRight: Boolean = false): Unit =
  IconProperty.provide(ActionLinkIcon.Custom(icon, atRight))

private val AutoHideOnDisableDescriptor =
  ComponentPropertyDescriptor<ActionLink, Boolean>(
    name = "autoHideOnDisable",
    read = { it.autoHideOnDisable },
    write = { link, value -> link.autoHideOnDisable = value },
  )

// The setter shows or hides the link from the value it is handed. Putting the value back runs that again, so the
// link ends up as visible as the value says, not as visible as it was found.
private val AutoHideOnDisableProperty =
  stylePropertyOf(AutoHideOnDisableDescriptor, restores = RestorePolicy.DeclaredPropertyOnly)

private val VisitedProperty =
  stylePropertyOf(
    ComponentPropertyDescriptor<ActionLink, Boolean>(
      name = "visited",
      read = { it.visited },
      write = { link, value -> link.visited = value },
    ),
  )

private val IconTextGapDescriptor =
  ComponentPropertyDescriptor<ActionLink, Int>(
    name = "iconTextGap",
    read = { it.iconTextGap },
    write = { link, value -> link.iconTextGap = value },
  )

private val HorizontalTextPositionDescriptor =
  ComponentPropertyDescriptor<ActionLink, Int>(
    name = "horizontalTextPosition",
    read = { it.horizontalTextPosition },
    write = { link, value -> link.horizontalTextPosition = value },
  )

/**
 * The link's icon. Every icon setter here, and the library's own `icon`, declares the `icon` property, so they share
 * one slot and the last one declared stands. Each `ActionLink` icon setter also writes the icon-text gap and the
 * horizontal text position, so those are given back with it.
 */
private val IconProperty =
  stylePropertyOf(
    ComponentPropertyDescriptor<ActionLink, ActionLinkIcon>(
      name = "icon",
      read = { ActionLinkIcon.Held(it.icon) },
      write = { link, value -> value.writeTo(link) },
    ),
    alsoOverwrites = listOf(IconTextGapDescriptor, HorizontalTextPositionDescriptor),
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
