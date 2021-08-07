// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import java.util.*

fun interface SimpleEventListener : EventListener {
  fun eventOccurred()

  companion object {

    fun addDisposableListener(dispatcher: EventDispatcher<SimpleEventListener>, disposable: Disposable, listener: () -> Unit) {
      dispatcher.addListener(SimpleEventListener { listener() }, disposable)
    }

    fun addListener(dispatcher: EventDispatcher<SimpleEventListener>, listener: () -> Unit) {
      dispatcher.addListener(SimpleEventListener { listener() })
    }

    fun addAndInvokeListener(dispatcher: EventDispatcher<SimpleEventListener>, listener: () -> Unit) {
      dispatcher.addListener(SimpleEventListener { listener() })
      listener()
    }

    fun addAndInvokeListener(dispatcher: EventDispatcher<SimpleEventListener>, disposable: Disposable, listener: () -> Unit) {
      dispatcher.addListener(SimpleEventListener { listener() }, disposable)
      if (!Disposer.isDisposed(disposable)) listener()
    }
  }
}