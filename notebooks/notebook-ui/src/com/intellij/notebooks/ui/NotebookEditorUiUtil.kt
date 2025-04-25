package com.intellij.notebooks.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableProperty
import javax.swing.JPanel
import javax.swing.plaf.PanelUI

/**
 * A workaround base class for panels with custom UI. Every look and feel change in the IDE (like theme, font family and size, etc.)
 * leads to call of the `updateUI()`. All default Swing implementations resets the UI to the default one.
 */
open class SteadyUIPanel(private val steadyUi: PanelUI) : JPanel() {
  init {
    // Required for correct UI initialization. It leaks in the super class anyway.
    @Suppress("LeakingThis") setUI(steadyUi)
  }

  override fun updateUI() {
    // Update the UI. Don't call super.updateUI() to prevent resetting to the default UI.
    // There's another way to set specific UI for specific components: by defining java.swing.plaf.ComponentUI#createUI and overriding
    // java.swing.JComponent#getUIClassID. This approach can't be used in our code since it requires UI classes to have empty constructors,
    // while in reality some additional data should be provided to UI instances in advance.
    setUI(steadyUi)
  }
}

fun <T> ObservableProperty<T>.bind(parentDisposable: Disposable, setter: (T) -> Unit) {
  setter(get())
  afterDistinctChange(parentDisposable) {
    setter(it)
  }
}

fun <T> ObservableProperty<T>.afterDistinctChange(parentDisposable: Disposable, setter: (T) -> Unit) {
  var prevValue: T? = null
  afterChange(parentDisposable) {
    if (prevValue == it)
      return@afterChange
    prevValue = it
    setter(it)
  }
}
