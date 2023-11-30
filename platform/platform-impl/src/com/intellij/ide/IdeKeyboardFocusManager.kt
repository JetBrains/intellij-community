// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.diagnostic.logger
import java.awt.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import javax.swing.DefaultFocusManager

/**
 * We extend the obsolete [DefaultFocusManager] class here instead of [KeyboardFocusManager] to prevent unwanted overwriting of
 * the default focus traversal policy by careless clients. In case they use the obsolete [javax.swing.FocusManager.getCurrentManager] method
 * instead of [KeyboardFocusManager.getCurrentKeyboardFocusManager], the former will override the default focus traversal policy,
 * if current focus manager doesn't extend [javax.swing.FocusManager]. We choose to extend [DefaultFocusManager], not just
 * [javax.swing.FocusManager] for the reasons described in [javax.swing.DelegatingDefaultFocusManager]'s javadoc -
 * just in case some legacy code expects it.
 */
internal class IdeKeyboardFocusManager : DefaultFocusManager() /* see javadoc above */ {
  // Don't inline this field, it's here to prevent policy override by parent's constructor. Don't make it final either.
  private val parentConstructorExecuted = true

  override fun dispatchEvent(e: AWTEvent): Boolean {
    if (EventQueue.isDispatchThread()) {
      var result = false
      performActivity(e) { result = super.dispatchEvent(e) }
      return result
    }
    else {
      return super.dispatchEvent(e)
    }
  }

  override fun setDefaultFocusTraversalPolicy(defaultPolicy: FocusTraversalPolicy) {
    if (parentConstructorExecuted) {
      val log = logger<IdeKeyboardFocusManager>()
      if (log.isDebugEnabled) {
        log.debug("setDefaultFocusTraversalPolicy: $defaultPolicy", Throwable())
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
}

@Suppress("IdentifierGrammar", "UNCHECKED_CAST")
internal fun replaceDefaultKeyboardFocusManager() {
  val manager = DefaultFocusManager.getCurrentKeyboardFocusManager()
  val newManager = IdeKeyboardFocusManager()
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
    logger<IdeKeyboardFocusManager>().error(e)
  }
  DefaultFocusManager.setCurrentKeyboardFocusManager(newManager)
}
