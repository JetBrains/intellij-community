// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.serialization.service.execution

import com.intellij.openapi.externalSystem.model.execution.TaskSettings
import com.intellij.openapi.externalSystem.model.execution.TaskSettingsImpl
import com.intellij.util.xmlb.annotations.Tag

@Tag("TaskSettings")
class TaskSettingsState(
  var name: String?,
  var arguments: List<String>?,
  var description: String?
) {

  @Suppress("unused")
  constructor() : this(null, null, null)

  fun toTaskSettings(): TaskSettingsImpl {
    return TaskSettingsImpl(name!!, arguments!!, description)
  }

  companion object {
    @JvmStatic
    fun valueOf(taskSettings: TaskSettings): TaskSettingsState {
      return TaskSettingsState(
        name = taskSettings.name,
        arguments = taskSettings.arguments,
        description = taskSettings.description
      )
    }
  }
}
