// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.modifier

import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.Icon

/** @see com.intellij.ui.components.ActionLink.autoHideOnDisable */
@ApiStatus.Experimental
public fun SwingModifier.autoHideOnDisable(value: Boolean): SwingModifier =
  this then ActionLinkPropertyElement(value, { it.autoHideOnDisable }, { link, v -> link.autoHideOnDisable = v })

/** @see com.intellij.ui.components.ActionLink.visited */
@ApiStatus.Experimental
public fun SwingModifier.visited(value: Boolean): SwingModifier =
  this then ActionLinkPropertyElement(value, { it.visited }, { link, v -> link.visited = v })

/** @see com.intellij.ui.components.ActionLink.setLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.linkIcon(): SwingModifier =
  this then ActionLinkIconElement(ActionLinkIcon.Link)

/** @see com.intellij.ui.components.ActionLink.setContextHelpIcon */
@ApiStatus.Experimental
public fun SwingModifier.contextHelpIcon(): SwingModifier =
  this then ActionLinkIconElement(ActionLinkIcon.ContextHelp)

/** @see com.intellij.ui.components.ActionLink.setExternalLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.externalLinkIcon(): SwingModifier =
  this then ActionLinkIconElement(ActionLinkIcon.ExternalLink)

/** @see com.intellij.ui.components.ActionLink.setDropDownLinkIcon */
@ApiStatus.Experimental
public fun SwingModifier.dropDownLinkIcon(): SwingModifier =
  this then ActionLinkIconElement(ActionLinkIcon.DropDownLink)

/** @see com.intellij.ui.components.ActionLink.setIcon */
@ApiStatus.Experimental
public fun SwingModifier.actionLinkIcon(icon: Icon, atRight: Boolean): SwingModifier =
  this then ActionLinkIconElement(ActionLinkIcon.Custom(icon, atRight))

/**
 * A [SwingModifier.Node] for a single [ActionLink] property: captures the pre-modifier value on
 * attach, writes the latest value on each apply, and restores the captured value on detach.
 */
private class ActionLinkPropertyNode<V>(
  private val read: (ActionLink) -> V,
  private val write: (ActionLink, V) -> Unit,
) : SwingModifier.Node<ActionLink>() {
  var value: () -> V = { error("ActionLink property value was not set before apply()") }
  private var restore: (() -> Unit)? = null

  override fun onAttach() {
    val link = component
    val original = read(link)
    restore = { write(link, original) }
  }

  fun apply() {
    write(component, value())
  }

  override fun onDetach() {
    restore?.invoke()
  }
}

// The declared value is compared structurally, and the accessors by identity: each builder passes
// non-capturing lambdas, so a rebuilt element carries the very same pair.
private data class ActionLinkPropertyElement<V>(
  private val value: V,
  private val read: (ActionLink) -> V,
  private val write: (ActionLink, V) -> Unit,
) : SwingModifier.NodeElement<ActionLink, ActionLinkPropertyNode<V>>() {
  override val targetType: Class<ActionLink> = ActionLink::class.java

  // Each builder declares its own write lambda (its own class), so distinct properties never share a
  // slot while repeated applications of one builder do (last wins).
  override val key: Any get() = write.javaClass

  override fun create(): ActionLinkPropertyNode<V> = ActionLinkPropertyNode(read, write)

  override fun update(node: ActionLinkPropertyNode<V>) {
    node.value = { value }
    node.apply()
  }
}

private sealed interface ActionLinkIcon {
  fun apply(actionLink: ActionLink)

  data object Link : ActionLinkIcon {
    override fun apply(actionLink: ActionLink) {
      actionLink.setLinkIcon()
    }
  }

  data object ContextHelp : ActionLinkIcon {
    override fun apply(actionLink: ActionLink) {
      actionLink.setContextHelpIcon()
    }
  }

  data object ExternalLink : ActionLinkIcon {
    override fun apply(actionLink: ActionLink) {
      actionLink.setExternalLinkIcon()
    }
  }

  data object DropDownLink : ActionLinkIcon {
    override fun apply(actionLink: ActionLink) {
      actionLink.setDropDownLinkIcon()
    }
  }

  data class Custom(private val icon: Icon, private val atRight: Boolean) : ActionLinkIcon {
    override fun apply(actionLink: ActionLink) {
      actionLink.setIcon(icon, atRight)
    }
  }
}

private data class ActionLinkIconState(
  val icon: Icon?,
  val iconTextGap: Int,
  val horizontalTextPosition: Int,
)

private class ActionLinkIconNode : SwingModifier.Node<ActionLink>() {
  var icon: ActionLinkIcon? = null
  private var restore: (() -> Unit)? = null

  override fun onAttach() {
    val link = component
    val saved = ActionLinkIconState(link.icon, link.iconTextGap, link.horizontalTextPosition)
    restore = {
      link.icon = saved.icon
      link.iconTextGap = saved.iconTextGap
      link.horizontalTextPosition = saved.horizontalTextPosition
    }
  }

  fun apply() {
    checkNotNull(icon) { "ActionLink icon was not set before apply()" }.apply(component)
  }

  override fun onDetach() {
    restore?.invoke()
  }
}

private data class ActionLinkIconElement(private val icon: ActionLinkIcon) :
  SwingModifier.NodeElement<ActionLink, ActionLinkIconNode>() {
  override val targetType: Class<ActionLink> = ActionLink::class.java

  override fun create(): ActionLinkIconNode = ActionLinkIconNode()

  override fun update(node: ActionLinkIconNode) {
    node.icon = icon
    node.apply()
  }
}
