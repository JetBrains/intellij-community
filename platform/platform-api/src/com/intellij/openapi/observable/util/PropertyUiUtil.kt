// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.Splitter
import java.awt.Dimension
import javax.swing.JComponent

val JComponent.heightProperty: ObservableProperty<Int>
  get() = sizeProperty.transform { it.height }

val JComponent.widthProperty: ObservableProperty<Int>
  get() = sizeProperty.transform { it.width }

val JComponent.sizeProperty: ObservableProperty<Dimension>
  get() = object : ObservableProperty<Dimension> {

    override fun get(): Dimension = size

    override fun afterChange(parentDisposable: Disposable?, listener: (Dimension) -> Unit) {
      whenSizeChanged(parentDisposable, listener)
    }
  }

val Splitter.proportionProperty: ObservableMutableProperty<Float>
  get() = object : ObservableMutableProperty<Float> {

    override fun get(): Float {
      return proportion
    }

    override fun set(value: Float) {
      proportion = value
    }

    override fun afterChange(parentDisposable: Disposable?, listener: (Float) -> Unit) {
      whenPropertyChanged<Float>(Splitter.PROP_PROPORTION, parentDisposable, listener)
    }
  }