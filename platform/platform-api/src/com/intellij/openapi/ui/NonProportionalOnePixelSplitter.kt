// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.OnePixelSplitter
import javax.swing.SwingUtilities

class NonProportionalOnePixelSplitter(
  vertical: Boolean,
  private val proportionKey: String,
  private val defaultProportion: Float = 0.5f,
  disposable: Disposable,
  private val project: Project?
) : OnePixelSplitter(vertical, proportionKey, defaultProportion) {

  private val propertiesComponent get() = if (project != null)  PropertiesComponent.getInstance(project) else PropertiesComponent.getInstance()

  private val size get() = if (orientation) height else width
  private val minSize get() = if (orientation) minimumSize.height else minimumSize.width

  init {
    Disposer.register(disposable, Disposable {
      saveProportion()
    })

    dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE
  }

  override fun addNotify() {
    super.addNotify()
    dividerPositionStrategy = DividerPositionStrategy.KEEP_PROPORTION
    invokeLaterWhen({ checkSize() }) {
      loadProportion()
      dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE
    }
  }

  private fun invokeLaterWhen(condition: () -> Boolean, action: () -> Unit) {
    SwingUtilities.invokeLater {
      if (condition()) {
        action()
      } else {
        invokeLaterWhen(condition, action)
      }
    }
  }

  private fun checkSize() = size != 0 && minSize < size

  override fun setProportion(proportion: Float) {
    if (proportion < 0 || proportion > 1) return

    super.setProportion(proportion)
  }

  override fun loadProportion() {
    if (!StringUtil.isEmpty(proportionKey)) {
      proportion = propertiesComponent.getFloat(proportionKey, myProportion)
    }
  }

  public override fun saveProportion() {
    if (!StringUtil.isEmpty(proportionKey)) {
      propertiesComponent.setValue(proportionKey, myProportion, defaultProportion)
    }
  }
}