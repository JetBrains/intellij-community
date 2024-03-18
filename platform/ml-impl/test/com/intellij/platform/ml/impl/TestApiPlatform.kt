// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.ExtensionController
import com.intellij.platform.ml.impl.logger.MLEvent
import com.intellij.platform.ml.impl.monitoring.MLApiStartupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener

abstract class TestApiPlatform : MLApiPlatform() {
  private val dynamicTaskListeners: MutableList<MLTaskGroupListener> = mutableListOf()
  private val dynamicStartupListeners: MutableList<MLApiStartupListener> = mutableListOf()
  private val dynamicEvents: MutableList<MLEvent> = mutableListOf()

  abstract val initialTaskListeners: List<MLTaskGroupListener>

  abstract val initialStartupListeners: List<MLApiStartupListener>

  abstract val initialEvents: List<MLEvent>

  final override val events: List<MLEvent>
    get() = initialEvents + dynamicEvents

  final override val taskListeners: List<MLTaskGroupListener>
    get() = initialTaskListeners + dynamicTaskListeners

  final override val startupListeners: List<MLApiStartupListener>
    get() = initialStartupListeners + dynamicStartupListeners

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    return extend(taskListener, dynamicTaskListeners)
  }

  override fun addEvent(event: MLEvent): ExtensionController {
    return extend(event, dynamicEvents)
  }

  override fun addStartupListener(listener: MLApiStartupListener): ExtensionController {
    return extend(listener, dynamicStartupListeners)
  }

  private fun <T> extend(obj: T, collection: MutableCollection<T>): ExtensionController {
    collection.add(obj)
    return ExtensionController { collection.remove(obj) }
  }
}
