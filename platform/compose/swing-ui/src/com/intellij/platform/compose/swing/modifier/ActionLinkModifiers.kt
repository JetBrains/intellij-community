// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.modifier

import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.Icon

/** @see com.intellij.ui.components.ActionLink.autoHideOnDisable */
@ApiStatus.Experimental
public fun SwingModifier.autoHideOnDisable(value: Boolean): SwingModifier =
  property<ActionLink, Boolean>(
    name = "autoHideOnDisable",
    value = value,
    read = { it.autoHideOnDisable },
    write = { link, declared -> link.autoHideOnDisable = declared },
    // The setter shows or hides the link from the value it is handed. Putting the value back runs that
    // again, so the link ends up as visible as the value says, not as visible as it was found.
    restores = RestorePolicy.DeclaredPropertyOnly,
  )

/** @see com.intellij.ui.components.ActionLink.visited */
@ApiStatus.Experimental
public fun SwingModifier.visited(value: Boolean): SwingModifier =
  property<ActionLink, Boolean>(
    name = "visited",
    value = value,
    read = { it.visited },
    write = { link, declared -> link.visited = declared },
  )

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
 * Declares the link's icon. Every icon builder writes through this one lambda, so the alternatives share
 * a slot and the last one declared stands.
 */
private fun SwingModifier.declaredIcon(icon: ActionLinkIcon): SwingModifier =
  property<ActionLink, ActionLinkIcon>(
    name = "icon",
    value = icon,
    read = { ActionLinkIcon.Held(it.icon, it.iconTextGap, it.horizontalTextPosition) },
    write = { link, declared -> declared.writeTo(link) },
  )

/**
 * An icon a link is given, and the icon a link was found carrying. Every setter here writes the icon, the
 * icon-text gap and the horizontal text position together, so [Held] carries all three and puts back
 * exactly what the modifier found.
 */
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

  data class Held(
    private val icon: Icon?,
    private val iconTextGap: Int,
    private val horizontalTextPosition: Int,
  ) : ActionLinkIcon {
    override fun writeTo(link: ActionLink) {
      link.icon = icon
      link.iconTextGap = iconTextGap
      link.horizontalTextPosition = horizontalTextPosition
    }
  }
}
