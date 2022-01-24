// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

/**
 * Deprecated. Please don't use this interface directly.
 * Clearable features of this interface aren't used.
 * Callback [afterChange] cannot be called when lazy property is [reset].
 * @see AtomicLazyProperty
 */
@Deprecated("Use instead ObservableMutableProperty")
interface ObservableClearableProperty<T> : ObservableMutableProperty<T> {

  fun reset()

  fun afterReset(listener: () -> Unit)

  fun afterReset(listener: () -> Unit, parentDisposable: Disposable)
}