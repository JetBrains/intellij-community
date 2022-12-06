// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.GeometryService
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.UIDefaults
import javax.swing.UIManager

class GeometryServiceImpl : GeometryService {

  private val uiDefaults: UIDefaults
    get() = UIManager.getDefaults()

  private val isInitialized: Boolean
    get() = uiDefaults.containsKey(ONE_OF_GEOMETRY_KEYS)

  override fun getSize(key: Key<Dimension>): Dimension = getValue(key) as Dimension

  private fun getValue(key: Key<*>): Any {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      EDT.assertIsEdt()
    }
    ensureInitialized()
    return uiDefaults.getValue(key.toString())
  }

  private fun <T : Any> putValue(key: Key<T>, value: T) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      EDT.assertIsEdt()
    }
    uiDefaults[key.toString()] = value
  }

  fun ensureInitialized() {
    if (!isInitialized) {
      fillDefaultValues()
    }
  }

  private fun fillDefaultValues() {
    putValue(ActionToolbar.EXPERIMENTAL_TOOLBAR_MINIMUM_BUTTON_SIZE_KEY, JBUI.size(40, 40))
  }

}

private val ONE_OF_GEOMETRY_KEYS = ActionToolbar.EXPERIMENTAL_TOOLBAR_MINIMUM_BUTTON_SIZE_KEY
