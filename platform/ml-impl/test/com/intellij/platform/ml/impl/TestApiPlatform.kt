// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.platform.ml.MLApiPlatform
import com.intellij.platform.ml.SystemLogger
import com.intellij.platform.ml.SystemLoggerBuilder
import com.intellij.platform.ml.monitoring.MLTaskGroupListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

abstract class TestApiPlatform : MLApiPlatform() {
  private val dynamicTaskListeners: MutableList<MLTaskGroupListener> = mutableListOf()

  abstract val initialTaskListeners: List<MLTaskGroupListener>

  final override val taskListeners: List<MLTaskGroupListener>
    get() = initialTaskListeners + dynamicTaskListeners

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    dynamicTaskListeners.add(taskListener)
    return object : ExtensionController {
      override fun remove() {
        require(dynamicTaskListeners.remove(taskListener))
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override val coroutineScope: CoroutineScope = GlobalScope

  override val systemLoggerBuilder: SystemLoggerBuilder = object : SystemLoggerBuilder {
    override fun build(clazz: Class<*>): SystemLogger {
      return object : SystemLogger {
        override fun info(data: () -> String) {
          Logger.getInstance(clazz).info(data())
        }

        override fun debug(data: () -> String) {
          Logger.getInstance(clazz).debug { data() }
        }
      }
    }
  }
}
