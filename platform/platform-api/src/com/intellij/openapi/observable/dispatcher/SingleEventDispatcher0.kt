// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * @see SingleEventDispatcher
 */
@ApiStatus.NonExtendable
interface SingleEventDispatcher0 {

  fun whenEventHappened(parentDisposable: Disposable?, listener: () -> Unit)
  fun whenEventHappened(listener: () -> Unit): Unit = whenEventHappened(null, listener)

  fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit)
  fun whenEventHappened(ttl: Int, listener: () -> Unit): Unit = whenEventHappened(ttl, null, listener)

  fun onceWhenEventHappened(parentDisposable: Disposable?, listener: () -> Unit)
  fun onceWhenEventHappened(listener: () -> Unit): Unit = onceWhenEventHappened(null, listener)

  fun getDelegateDispatcher(): SingleEventDispatcher<Nothing?>

  /**
   * @see SingleEventDispatcher.Multicaster
   */
  @ApiStatus.NonExtendable
  interface Multicaster : SingleEventDispatcher0 {

    fun fireEvent()
  }
}