// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal abstract class AbstractSingleEventDispatcher0 : SingleEventDispatcher0 {

  protected abstract val delegate: SingleEventDispatcher<Nothing?>

  override fun whenEventHappened(parentDisposable: Disposable?, listener: () -> Unit) {
    delegate.whenEventHappened(parentDisposable) { listener() }
  }

  override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit) {
    delegate.whenEventHappened(ttl, parentDisposable) { listener() }
  }

  override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: () -> Unit) {
    delegate.onceWhenEventHappened(parentDisposable) { listener() }
  }

  override fun getDelegateDispatcher(): SingleEventDispatcher<Nothing?> {
    return delegate
  }

  @ApiStatus.Internal
  class RootDispatcher : AbstractSingleEventDispatcher0(),
                         SingleEventDispatcher0.Multicaster {

    override val delegate = AbstractSingleEventDispatcher.RootDispatcher<Nothing?>()

    override fun fireEvent() {
      delegate.fireEvent(null)
    }
  }

  @ApiStatus.Internal
  class DelegateDispatcher(
    override val delegate: SingleEventDispatcher<Nothing?>
  ) : AbstractSingleEventDispatcher0()
}