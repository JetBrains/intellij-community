// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.properties.Delegates

@ApiStatus.Internal
open class CombinedDiffSelectablePanel(private val regularBackground: Color,
                                       onClick: () -> Unit) : BorderLayoutPanel() {

  var focused: Boolean by Delegates.observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      updateBackground(selected, newValue)
    }
  }

  var selected: Boolean by Delegates.observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      updateBackground(newValue, focused)
    }
  }

  private val mouseListener = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      onClick()
    }
  }

  init {
    background = regularBackground

    setupListeners()
  }

  protected open fun getSelectionBackground(state: State): Color {
    return UIUtil.getListBackground(state.selected, state.focused)
  }

  protected open fun getHoverBackground(state: State): Color {
    return UIUtil.getListBackground(false, state.focused)
  }

  protected open fun changeBackgroundOnHover(state: State): Boolean {
    return !state.selected
  }

  final override fun add(comp: Component, constraints: Any?) {
    if (comp is ActionToolbarImpl) {
      comp.component.addMouseListener(mouseListener)
    }
    super.add(comp, constraints)
  }

  private fun setupListeners() {
    addHoverAndPressStateListener(this,
                                  { _, hovered ->
                                    updateBackground(selected, focused, hovered)
                                  })
    addMouseListener(mouseListener)
  }

  private fun updateBackground(selected: Boolean, focused: Boolean, hovered: Boolean = false) {
    val state = State(selected, focused, hovered)
    UIUtil.changeBackGround(this,
                            when {
                              hovered && changeBackgroundOnHover(state) -> getHoverBackground(state)
                              selected -> getSelectionBackground(state)
                              else -> regularBackground
                            })
  }

  protected data class State(val selected: Boolean, val focused: Boolean, val hovered: Boolean)
}
