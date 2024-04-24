// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

abstract class TestApiPlatform : MLApiPlatform() {
  private val dynamicTaskListeners: MutableList<MLTaskGroupListener> = mutableListOf()

  abstract val initialTaskListeners: List<MLTaskGroupListener>

  final override val taskListeners: List<MLTaskGroupListener>
    get() = initialTaskListeners + dynamicTaskListeners

  override fun addTaskListener(taskListener: MLTaskGroupListener, parentDisposable: Disposable) {
    dynamicTaskListeners.add(taskListener)
    parentDisposable.whenDisposed {
      require(dynamicTaskListeners.remove(taskListener))
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override val coroutineScope: CoroutineScope = GlobalScope
}
