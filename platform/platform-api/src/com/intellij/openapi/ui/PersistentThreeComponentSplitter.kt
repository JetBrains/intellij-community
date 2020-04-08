// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

class PersistentThreeComponentSplitter(
  vertical: Boolean,
  onePixelDivider: Boolean,
  proportionKey: String,
  disposable: Disposable,
  private val project: Project?,
  private val defaultFirstProportion: Float = 0.3f,
  private val defaultLastProportion: Float = 0.5f
) : ThreeComponentsSplitter(vertical, onePixelDivider, disposable) {

  companion object {
    private const val firstSuffixKey = "_PTCS_FirstProportionKey"
    private const val lastSuffixKey = "_PTCS_LastProportionKey"
  }

  private val firstProportionKey = "$proportionKey$firstSuffixKey"
  private val lastProportionKey = "$proportionKey$lastSuffixKey"

  private var firstProportion: Float
    get() = getProportion(firstProportionKey, defaultFirstProportion)
    set(value) = setProportion(firstProportionKey, value, defaultFirstProportion)

  private var lastProportion: Float
    get() = getProportion(lastProportionKey, defaultLastProportion)
    set(value) = setProportion(lastProportionKey, value, defaultLastProportion)

  private val propertiesComponent get() = if (project != null)  PropertiesComponent.getInstance(project) else PropertiesComponent.getInstance()

  private fun getProportion(key: String, defaultProportion: Float): Float = propertiesComponent.getFloat(key, defaultProportion)

  private fun setProportion(key: String, value: Float, defaultProportion: Float) {
    if (value < 0 || value > 1) return

    propertiesComponent.setValue(key, value, defaultProportion)
  }

  private val totalSize get() = if (orientation) height else width
  private val totalMinSize get() = if (orientation) minimumSize.height else minimumSize.width

  private var addNotifyCalled = false
  private var layoutIsRunning = false

  private val shouldLayout get() = addNotifyCalled && !layoutIsRunning

  init {
    Disposer.register(disposable, Disposable {
      saveProportions()
    })
  }

  fun saveProportions() {
    if (!checkSize()) return

    firstProportion = firstSize / (totalSize - 2.0f * dividerWidth)
    lastProportion = lastSize / (totalSize - 2.0f * dividerWidth)
  }

  fun restoreProportions() {
    setFirstSize()
    setLastSize()
  }

  override fun addNotify() {
    super.addNotify()
    addNotifyCalled = true
    invokeLaterWhen({ checkSize() }) {
      addNotifyCalled = false
      restoreProportions()
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

  override fun doLayout() {
    if (shouldLayout) {
      doLayoutUnderGuard {
        restoreProportions()
      }
    }

    super.doLayout()
  }

  private fun doLayoutUnderGuard(action: () -> Unit) {
    layoutIsRunning = true
    try {
      action()
    }
    finally {
      layoutIsRunning = false
    }
  }

  private fun setFirstSize() {
    if (totalSize <= totalMinSize) return

    firstSize = (firstProportion * (totalSize - 2 * dividerWidth)).roundToInt()
  }

  private fun setLastSize() {
    if (totalSize <= totalMinSize) return

    lastSize = (lastProportion * (totalSize - 2 * dividerWidth)).roundToInt()
  }

  private fun checkSize(): Boolean {
    return totalMinSize < totalSize && firstSize > 0 && lastSize > 0
  }
}
