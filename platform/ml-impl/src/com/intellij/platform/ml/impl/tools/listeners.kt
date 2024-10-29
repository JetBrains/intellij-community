// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLTaskListenerTyped<M : com.jetbrains.ml.model.MLModel<P>, P : Any> : com.jetbrains.ml.monitoring.MLTaskListenerTyped<M, P>, MessageBusMLTaskListenerProvider<M, P> {
  val task: com.jetbrains.ml.MLTask<M, P>

  override fun provide(collector: (com.jetbrains.ml.monitoring.MLTaskListenerTyped<M, P>, String) -> Unit) {
    collector(this, task.id)
  }

  companion object {
    val TOPIC: Topic<MessageBusMLTaskListenerProvider<*, *>> = Topic.create<MessageBusMLTaskListenerProvider<*, *>>("ml.task", MessageBusMLTaskListenerProvider::class.java)
  }
}

@ApiStatus.Internal
interface MLTaskLoggingListener : com.jetbrains.ml.monitoring.MLTaskLoggingListener, MessageBusMLTaskLoggingListenerProvider {
  val task: com.jetbrains.ml.MLTask<*, *>

  override fun provide(collector: (com.jetbrains.ml.monitoring.MLTaskLoggingListener, String) -> Unit) {
    collector(this, task.id)
  }

  companion object {
    val TOPIC = Topic.create<MessageBusMLTaskLoggingListenerProvider>("ml.logging", MessageBusMLTaskLoggingListenerProvider::class.java)
  }
}
