// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable


fun <T> ObservableProperty<T>.whenPropertyChanged(listener: (T) -> Unit) = whenPropertyChanged(null, listener)
fun <T> ObservableProperty<T>.whenPropertyChanged(parentDisposable: Disposable?, listener: (T) -> Unit) =
  afterChange(parentDisposable, listener)

fun ObservableBooleanProperty.whenPropertySet(listener: () -> Unit) = whenPropertySet(null, listener)
fun ObservableBooleanProperty.whenPropertySet(parentDisposable: Disposable?, listener: () -> Unit) =
  afterSet(parentDisposable, listener)

fun ObservableBooleanProperty.whenPropertyReset(listener: () -> Unit) = whenPropertyReset(null, listener)
fun ObservableBooleanProperty.whenPropertyReset(parentDisposable: Disposable?, listener: () -> Unit) =
  afterReset(parentDisposable, listener)