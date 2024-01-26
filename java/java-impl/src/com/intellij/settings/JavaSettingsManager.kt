// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.logging.UnspecifiedLogger
import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "JavaSettingsStorage",
       storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JavaSettingsStorage : SimplePersistentStateComponent<JavaSettingsStorage.State>(State()) {
  class State : BaseState() {
    var loggerName: String? by string(UnspecifiedLogger.UNSPECIFIED_LOGGER_NAME)
  }
}

