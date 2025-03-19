// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.logging

import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "JvmLoggingSettingsStorage",
       storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JvmLoggingSettingsStorage : SimplePersistentStateComponent<JvmLoggingSettingsStorage.State>(State()) {
  class State : BaseState() {
    var loggerId: String? by string(UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)

    var loggerName: String? by string(JvmLoggerFieldDelegate.LOGGER_IDENTIFIER)
  }
}

