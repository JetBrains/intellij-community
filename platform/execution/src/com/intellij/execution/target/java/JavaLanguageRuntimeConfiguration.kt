// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class JavaLanguageRuntimeConfiguration : LanguageRuntimeConfiguration(JavaLanguageRuntimeTypeConstants.TYPE_ID),
                                         PersistentStateComponent<JavaLanguageRuntimeConfiguration.MyState> {
  var homePath: String = ""
  var javaVersionString: String = ""

  override fun getState(): MyState = MyState().also {
    it.homePath = this.homePath
    it.javaVersionString = this.javaVersionString

    saveInState(JavaLanguageRuntimeTypeConstants.CLASS_PATH_VOLUME) { volumeState ->
      it.classpathFolder = volumeState
    }
    saveInState(JavaLanguageRuntimeTypeConstants.AGENTS_VOLUME) { volumeState ->
      it.agentFolder = volumeState
    }
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.javaVersionString = state.javaVersionString ?: ""

    loadVolumeState(JavaLanguageRuntimeTypeConstants.CLASS_PATH_VOLUME, state.classpathFolder)
    loadVolumeState(JavaLanguageRuntimeTypeConstants.AGENTS_VOLUME, state.agentFolder)
  }

  @Throws(RuntimeConfigurationException::class)
  override fun validateConfiguration() {
    super.validateConfiguration()
    if (homePath.isBlank()) {
      throw RuntimeConfigurationException(ExecutionBundle.message("JavaLanguageRuntimeConfiguration.error.java.home.is.required"))
    }
  }

  class MyState : BaseState() {
    var homePath: String? by string()
    var javaVersionString: String? by string()

    var applicationFolder: VolumeState? by property()
    var classpathFolder: VolumeState? by property()
    var agentFolder: VolumeState? by property()
  }
}