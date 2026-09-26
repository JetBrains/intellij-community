// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.logging

import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "JvmLoggingSettingsStorage",
       storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
public class JvmLoggingSettingsStorage : SimplePersistentStateComponent<JvmLoggingSettingsStorage.State>(State()) {
  public class State : BaseState() {
    public var loggerId: String? by string(UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)

    public var loggerName: String? by string(JvmLoggerFieldDelegate.LOGGER_IDENTIFIER)
  }
}

