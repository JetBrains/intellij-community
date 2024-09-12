// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "JavaGeneratorSettingsStorage",
       storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JavaGeneratorSettingsStorage : SimplePersistentStateComponent<JavaGeneratorSettingsStorage.State>(State()) {
  class State : BaseState() {
    var generateAllAnnotations: Boolean by property(false)
  }
}

