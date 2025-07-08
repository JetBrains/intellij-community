// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComponentUtil
import com.intellij.util.concurrency.createChildContextIgnoreStructuredConcurrency
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector.Companion.doWhenFirstShown
import com.intellij.util.ui.update.UiNotifyConnector.ContextActivatable.Companion.wrapIfNeeded
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * ### Obsolescence notice
 *
 * Use [com.intellij.util.ui.launchOnShow]/[com.intellij.util.ui.launchOnceOnShow] instead.
 */
@Obsolete
open class UiNotifyConnector : Disposable, HierarchyListener {
  private val component: WeakReference<Component>
  private var target: Activatable?
  private val isDeferred: Boolean

  /**
   * @param ignored parameter is used to avoid clash with the deprecated constructor
   */
  @Suppress("UNUSED_PARAMETER")
  protected constructor(component: Component, target: Activatable, isDeferred: Boolean, ignored: Any?) {
    this.component = WeakReference(component)
    this.target = target.wrapIfNeeded()
    this.isDeferred = isDeferred
  }

  @Deprecated(
    """Use the static method {@link UiNotifyConnector#installOn(Component, Activatable, boolean)}.
    <p>
    For inheritance, use the non-deprecated constructor.
    <p>
    Also, note that non-deprecated constructor is side effect free, and you should call for {@link UiNotifyConnector#setupListeners()}
    method""")
  constructor(component: Component, target: Activatable) {
    this.component = WeakReference(component)
    this.target = target.wrapIfNeeded()
    isDeferred = true
    setupListeners(component)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated(
    """Use the static method {@link UiNotifyConnector#installOn(Component, Activatable, boolean)}.
    <p>
    For inheritance use the non-deprecated constructor.
    <p>
    Also, note that non-deprecated constructor is side effect free, and you should call for {@link UiNotifyConnector#setupListeners()}
    method""")
  constructor(component: Component, target: Activatable, deferred: Boolean) {
    this.component = WeakReference(component)
    this.target = target.wrapIfNeeded()
    isDeferred = deferred
    setupListeners(component)
  }

  private class ContextActivatable(private val target: Activatable) : Activatable {
    companion object {
      fun Activatable.wrapIfNeeded(): ContextActivatable = this as? ContextActivatable ?: ContextActivatable(this)
    }

    private val childContext = createChildContextIgnoreStructuredConcurrency(ContextActivatable::class.java.name)

    override fun showNotify() {
      resetThreadContext {
        childContext.runInChildContext { target.showNotify() }
      }
    }

    override fun hideNotify() {
      resetThreadContext {
        childContext.runInChildContext { target.hideNotify() }
      }
    }
  }

  companion object {

    /**
     * ### Obsolescence notice
     *
     * Use [com.intellij.util.ui.launchOnShow] instead.
     */
    @Obsolete
    @JvmStatic
    fun installOn(component: Component, target: Activatable, deferred: Boolean): UiNotifyConnector {
      val connector = UiNotifyConnector(component, target, deferred, null)
      connector.setupListeners(component)
      return connector
    }

    /**
     * ### Obsolescence notice
     *
     * Use [com.intellij.util.ui.launchOnShow] instead.
     */
    @Obsolete
    @JvmStatic
    fun installOn(component: Component, target: Activatable): UiNotifyConnector {
      val connector = UiNotifyConnector(component = component, target = target, isDeferred = true, ignored = null)
      connector.setupListeners(component)
      return connector
    }

    /**
     * ### Obsolescence notice
     *
     * Use [com.intellij.util.ui.launchOnceOnShow] instead.
     */
    @Obsolete
    @JvmStatic
    fun doWhenFirstShown(component: JComponent, runnable: Runnable) {
      doWhenFirstShown(component = component, runnable = runnable, parent = null)
    }

    /**
     * ### Obsolescence notice
     *
     * Use [com.intellij.util.ui.launchOnceOnShow] instead.
     */
    @Obsolete
    fun doWhenFirstShown(component: Component, isDeferred: Boolean = true, runnable: () -> Unit) {
      doWhenFirstShown(
        component = component,
        activatable = object : Activatable {
          override fun showNotify() {
            runnable()
          }
        },
        parent = null,
        isDeferred = isDeferred,
      )
    }

    /**
     * ### Obsolescence notice
     *
     * Use [com.intellij.util.ui.launchOnceOnShow] instead.
     */
    @JvmOverloads
    @JvmStatic
    fun doWhenFirstShown(component: Component, runnable: Runnable, parent: Disposable? = null) {
      doWhenFirstShown(
        component = component,
        isDeferred = true,
        activatable = object : Activatable {
          override fun showNotify() {
            runnable.run()
          }
        },
        parent = parent,
      )
    }

    private fun doWhenFirstShown(component: Component, activatable: Activatable, isDeferred: Boolean, parent: Disposable?) {
      val connector = object : UiNotifyConnector(component = component, target = activatable, isDeferred = isDeferred, ignored = null) {
        override fun showNotify() {
          super.showNotify()
          Disposer.dispose(this)
        }
      }
      connector.setupListeners(component)
      if (parent != null) {
        Disposer.register(parent, connector)
      }
    }

    /**
     * Attention! This does not trigger [com.intellij.util.ui.launchOnShow]/[com.intellij.util.ui.launchOnceOnShow].
     * See IJPL-175524
     */
    @ApiStatus.Experimental
    fun forceNotifyIsShown(c: Component) {
      for (child in UIUtil.uiTraverser(c)) {
        if (UIUtil.isShowing(child, false)) {
          for (listener in child.hierarchyListeners) {
            if (listener is UiNotifyConnector && !listener.isDisposed) {
              listener.showNotify()
            }
          }
        }
      }
    }
  }

  protected fun setupListeners(component: Component) {
    if (ComponentUtil.isShowing(component, false)) {
      showNotify()
    }
    else {
      hideNotify()
    }
    if (isDisposed) {
      return
    }
    component.addHierarchyListener(this)
  }

  override fun hierarchyChanged(e: HierarchyEvent) {
    if (isDisposed || (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) <= 0) {
      return
    }

    val runnable = Runnable {
      val component = component.get()?.takeIf { !isDisposed } ?: return@Runnable
      if (ComponentUtil.isShowing(component, false)) {
        showNotify()
      }
      else {
        hideNotify()
      }
    }

    if (isDeferred) {
      val app = ApplicationManager.getApplication()
      if (app != null && app.isDispatchThread) {
        app.invokeLater(runnable, ModalityState.current())
      }
      else {
        SwingUtilities.invokeLater(runnable)
      }
    }
    else {
      runnable.run()
    }
  }

  protected open fun hideNotify() {
    target!!.hideNotify()
  }

  protected open fun showNotify() {
    target!!.showNotify()
  }

  protected open fun hideOnDispose() {
    target!!.hideNotify()
  }

  override fun dispose() {
    if (isDisposed) {
      return
    }

    hideOnDispose()
    val c = component.get()
    c?.removeHierarchyListener(this)

    target = null
    component.clear()
  }

  private val isDisposed: Boolean
    get() = target == null

  /**
   * ### Obsolescence notice
   *
   * Use [com.intellij.util.ui.launchOnceOnShow] instead.
   */
  @Obsolete
  class Once : UiNotifyConnector {
    private var isShown = false
    private var isHidden = false

    /**
     * Use [method][doWhenFirstShown]
     * @param ignored parameter is used to avoid clash with the deprecated constructor
     */
    @Suppress("UNUSED_PARAMETER", "DEPRECATION")
    private constructor(component: Component, target: Activatable, ignored: Any?) : super(component = component, target = target)

    companion object {

      /**
       * ### Obsolescence notice
       *
       * Use [com.intellij.util.ui.launchOnceOnShow] instead.
       */
      @Obsolete
      @JvmStatic
      fun installOn(component: Component, target: Activatable): Once {
        val once = Once(component = component, target = target, ignored = null)
        once.setupListeners(component)
        return once
      }
    }

    @Suppress("DEPRECATION")
    @Deprecated(
      """Use the static method {@link Once#installOn(Component, Activatable, boolean)}.
      <p>
      Also, note that non-deprecated constructor is side effect free, and you should call for {@link Once#setupListeners()}
      method""", level = DeprecationLevel.ERROR)
    constructor(component: Component, target: Activatable) : super(component = component, target = target)

    override fun hideNotify() {
      super.hideNotify()
      isHidden = true
      disposeIfNeeded()
    }

    override fun showNotify() {
      super.showNotify()
      isShown = true
      disposeIfNeeded()
    }

    override fun hideOnDispose() {}

    private fun disposeIfNeeded() {
      if (isShown && isHidden) {
        Disposer.dispose(this)
      }
    }
  }
}
