// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.ide.ui.ShowingContainer
import com.intellij.idea.AppMode
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED
import java.awt.event.HierarchyEvent.SHOWING_CHANGED
import java.awt.event.WindowEvent
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import javax.swing.DefaultFocusManager

private val logger = logger<IdeKeyboardFocusManager>()

/**
 * We extend the obsolete [DefaultFocusManager] class here instead of [KeyboardFocusManager] to prevent unwanted overwriting of
 * the default focus traversal policy by careless clients. In case they use the obsolete [javax.swing.FocusManager.getCurrentManager] method
 * instead of [KeyboardFocusManager.getCurrentKeyboardFocusManager], the former will override the default focus traversal policy,
 * if current focus manager doesn't extend [javax.swing.FocusManager]. We choose to extend [DefaultFocusManager], not just
 * [javax.swing.FocusManager] for the reasons described in [javax.swing.DelegatingDefaultFocusManager]'s javadoc -
 * just in case some legacy code expects it.
 */
internal class IdeKeyboardFocusManager(internal val original: KeyboardFocusManager) : DefaultFocusManager() /* see javadoc above */ {
  // Don't inline this field, it's here to prevent policy override by parent's constructor. Don't make it final either.
  private val parentConstructorExecuted = true

  override fun dispatchEvent(e: AWTEvent): Boolean {
    if (e.id == HierarchyEvent.HIERARCHY_CHANGED &&
        e is HierarchyEvent &&
        e.changeFlags.toInt().and(DISPLAYABILITY_CHANGED or SHOWING_CHANGED) == DISPLAYABILITY_CHANGED &&
        isRecursivelyVisibleViaShowingContainer(e.component)) {
      // Hack to support SHOWING_CHANGED event generation for ShowingContainer
      val patchedEvent = HierarchyEvent(e.component, e.id, e.changed, e.changedParent, e.changeFlags.or(SHOWING_CHANGED.toLong()))
      e.component.dispatchEvent(patchedEvent)
      return true
    }
    val dispatch = { getAssociatedClientId(e).use { super.dispatchEvent(e) } }
    if (EventQueue.isDispatchThread()) {
      var result = false
      val app = ApplicationManager.getApplication()
      // Don't try to get WIRA if we are in read action or there is no application at all
      if (app == null || app.isReadAccessAllowed) {
        performActivity(e, false) { result = dispatch() }
      }
      else {
        //todo fix all clients and remove WIRA here, but for now it is like keyboard or mouse event
        performActivity(e, false) { WriteIntentReadAction.run { result = dispatch() } }
      }
      return result
    }
    else {
      return dispatch()
    }
  }

  private fun getAssociatedClientId(e: AWTEvent) : AccessToken {
    if (!AppMode.isRemoteDevHost()) return AccessToken.EMPTY_ACCESS_TOKEN
    val id = e.id
    if (id == WindowEvent.WINDOW_GAINED_FOCUS ||
        id == WindowEvent.WINDOW_LOST_FOCUS ||
        id == FocusEvent.FOCUS_GAINED ||
        id == FocusEvent.FOCUS_LOST) {
      if (e is ClientIdAwareEvent) {
        logger.debug { "$e is ${ClientIdAwareEvent::class.simpleName}. Wrapping with ${e.clientId}" }
        return withClientId(e.clientId)
      }
      else {
        logger.debug { "$e is not ${ClientIdAwareEvent::class.simpleName}. Falling back to wrapping with a controller's ClientId" }
        ClientSessionsManager.getAppSessions(ClientKind.CONTROLLER).firstOrNull()?.let {
          return withClientId(it.clientId)
        }
      }
    }
    return AccessToken.EMPTY_ACCESS_TOKEN
  }

  override fun setDefaultFocusTraversalPolicy(defaultPolicy: FocusTraversalPolicy) {
    if (parentConstructorExecuted) {
      if (logger.isDebugEnabled) {
        logger.debug("setDefaultFocusTraversalPolicy: $defaultPolicy", Throwable())
      }
      super.setDefaultFocusTraversalPolicy(defaultPolicy)
    }
  }

  override fun setGlobalFocusOwner(focusOwner: Component?) {
    // Check against recursively invisible components.
    // The calling code does check isShowing(), but it can be overridden by ShowingController.
    var c = focusOwner
    while (c != null && c !is Window) {
      if (!c.isVisible) return
      c = c.parent
    }
    super.setGlobalFocusOwner(focusOwner)
  }

  private fun isRecursivelyVisibleViaShowingContainer(component: Component): Boolean {
    var c = component
    while (true) {
      if (c is ShowingContainer) return true
      if (!c.isVisible || c is Window) return false
      c = c.parent ?: return false
    }
  }
}

@Suppress("IdentifierGrammar", "UNCHECKED_CAST")
internal fun replaceDefaultKeyboardFocusManager() {
  val manager = DefaultFocusManager.getCurrentKeyboardFocusManager()
  val newManager = IdeKeyboardFocusManager(manager)
  for (l in manager.propertyChangeListeners) {
    newManager.addPropertyChangeListener(l)
  }
  for (l in manager.vetoableChangeListeners) {
    newManager.addVetoableChangeListener(l)
  }

  try {
    val lookup = MethodHandles.privateLookupIn(KeyboardFocusManager::class.java, MethodHandles.lookup())
    val listType = MethodType.methodType(List::class.java)
    val getDispatchersMethod = lookup.findVirtual(KeyboardFocusManager::class.java, "getKeyEventDispatchers", listType)
    val dispatchers = getDispatchersMethod.invoke(manager) as? List<KeyEventDispatcher>
    if (dispatchers != null) {
      for (d in dispatchers) {
        newManager.addKeyEventDispatcher(d)
      }
    }
    val getPostProcessorsMethod = lookup.findVirtual(KeyboardFocusManager::class.java, "getKeyEventPostProcessors", listType)
    val postProcessors = getPostProcessorsMethod.invoke(manager) as? List<KeyEventPostProcessor>
    if (postProcessors != null) {
      for (p in postProcessors) {
        newManager.addKeyEventPostProcessor(p)
      }
    }
  }
  catch (e: Exception) {
    logger.error(e)
  }
  DefaultFocusManager.setCurrentKeyboardFocusManager(newManager)
}

internal fun restoreDefaultKeyboardFocusManager() {
  (DefaultFocusManager.getCurrentKeyboardFocusManager() as? IdeKeyboardFocusManager)?.let {
    DefaultFocusManager.setCurrentKeyboardFocusManager(it.original)
  }
}
