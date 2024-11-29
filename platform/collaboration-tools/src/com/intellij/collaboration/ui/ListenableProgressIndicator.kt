// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.util.EventDispatcher

class ListenableProgressIndicator : ProgressIndicatorBase() {
  private val eventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override fun isReuseable() = true
  override fun onProgressChange() = invokeAndWaitIfNeeded { eventDispatcher.multicaster.eventOccurred() }
  fun addAndInvokeListener(listener: () -> Unit) = SimpleEventListener.addAndInvokeListener(eventDispatcher, listener)
  fun addAndInvokeListener(disposable: Disposable, listener: () -> Unit) {
    SimpleEventListener.addAndInvokeListener(eventDispatcher, disposable, listener)
  }
}