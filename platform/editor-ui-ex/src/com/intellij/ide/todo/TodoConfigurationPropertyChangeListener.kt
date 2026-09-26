// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener

@ApiStatus.Internal
fun interface TodoConfigurationPropertyChangeListener : PropertyChangeListener {

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<TodoConfigurationPropertyChangeListener> =
      Topic(TodoConfigurationPropertyChangeListener::class.java.simpleName, TodoConfigurationPropertyChangeListener::class.java)
  }
}
