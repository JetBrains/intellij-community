// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "JavaSettingsStorage",
       storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JavaSettingsStorage : SimplePersistentStateComponent<JavaSettingsStorage.State>(State())  {
  class State : BaseState() {
    var logger by enum<JvmLoggerType>(JvmLoggerType.JAVA_LOGGING)
  }
}

