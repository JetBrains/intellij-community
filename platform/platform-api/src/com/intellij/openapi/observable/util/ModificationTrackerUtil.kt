// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import javax.swing.JTextField

operator fun ModificationTracker.plus(tracker: ModificationTracker): ModificationTracker {
  return ModificationTracker {
    modificationCount + tracker.modificationCount
  }
}

fun <T> ObservableMutableProperty<T>.createPropertyModificationTracker(parentDisposable: Disposable? = null): ModificationTracker {
  val modificationTracker = SimpleModificationTracker()
  whenPropertyChanged(parentDisposable) {
    modificationTracker.incModificationCount()
  }
  return modificationTracker
}

fun JTextField.createTextModificationTracker(parentDisposable: Disposable? = null): ModificationTracker {
  val modificationTracker = SimpleModificationTracker()
  whenTextChanged(parentDisposable) {
    modificationTracker.incModificationCount()
  }
  return modificationTracker
}