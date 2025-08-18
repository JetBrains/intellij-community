// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.LayoutManager
import java.util.function.Function
import javax.swing.JPanel

/**
 * @author Konstantin Bulenkov
 */
open class JBLoadingPanel(manager: LayoutManager?,
                          createLoadingDecorator: Function<JPanel, LoadingDecorator>) : JPanel(BorderLayout()) {
  val contentPanel: JPanel
  private val decorator: LoadingDecorator?
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<JBLoadingPanelListener>()

  @JvmOverloads
  constructor(manager: LayoutManager?, parent: Disposable, startDelayMs: Int = -1) : this(manager = manager,
                                                                                          createLoadingDecorator = { panel ->
                                                                                            LoadingDecorator(panel, parent, startDelayMs)
                                                                                          })

  constructor(manager: LayoutManager?, parent: Disposable, startDelayMs: Long)
    : this(manager = manager,
           createLoadingDecorator = { panel -> LoadingDecorator(panel, parent, startDelayMs.toInt()) })

  init {
    contentPanel = manager?.let { JPanel(it) } ?: JPanel()
    contentPanel.isOpaque = false
    contentPanel.isFocusable = false
    decorator = createLoadingDecorator.apply(contentPanel)

    super.add(decorator.component, BorderLayout.CENTER)
  }

  override fun setLayout(layoutManager: LayoutManager) {
    require(layoutManager is BorderLayout) { layoutManager.toString() }
    super.setLayout(layoutManager)
    decorator?.component?.let {
      super.add(it, BorderLayout.CENTER)
    }
  }

  fun setLoadingText(text: @Nls String?) {
    decorator!!.loadingText = text
  }

  fun getLoadingText(): String? {
    return decorator!!.loadingText
  }

  open fun stopLoading() {
    decorator!!.stopLoading()
    for (listener in listeners) {
      listener.onLoadingFinish()
    }
  }

  val isLoading: Boolean
    get() = decorator!!.isLoading

  open fun startLoading() {
    decorator!!.startLoading(false)
    for (listener in listeners) {
      listener.onLoadingStart()
    }
  }

  fun addListener(listener: JBLoadingPanelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: JBLoadingPanelListener): Boolean {
    return listeners.remove(listener)
  }

  override fun add(comp: Component): Component {
    return contentPanel.add(comp)
  }

  override fun add(comp: Component, index: Int): Component {
    return contentPanel.add(comp, index)
  }

  override fun add(comp: Component, constraints: Any) {
    contentPanel.add(comp, constraints)
  }

  override fun getPreferredSize(): Dimension {
    return contentPanel.preferredSize
  }
}