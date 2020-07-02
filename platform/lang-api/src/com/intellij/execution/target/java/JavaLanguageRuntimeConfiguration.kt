// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class JavaLanguageRuntimeConfiguration : LanguageRuntimeConfiguration(JavaLanguageRuntimeType.TYPE_ID),
                                         PersistentStateComponent<JavaLanguageRuntimeConfiguration.MyState> {
  var homePath: String = ""
  var javaVersionString: String = ""

  override fun getState() = MyState().also {
    it.homePath = this.homePath
    it.javaVersionString = this.javaVersionString

    it.saveInState(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME) { path, bit ->
      applicationFolder = path
      applicationTargetSpecificBit = bit
    }
    it.saveInState(JavaLanguageRuntimeType.CLASS_PATH_VOLUME) { path, bit ->
      classpathFolder = path
      classpathTargetSpecificBit = bit
    }
    it.saveInState(JavaLanguageRuntimeType.AGENTS_VOLUME) { path, bit ->
      agentFolder = path
      agentTargetSpecificBit = bit
    }
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.javaVersionString = state.javaVersionString ?: ""

    setTargetPath(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME, state.applicationFolder)
    setTargetPath(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, state.classpathFolder)
    setTargetPath(JavaLanguageRuntimeType.AGENTS_VOLUME, state.agentFolder)

    state.applicationTargetSpecificBit?.let { setTargetSpecificData(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME, it) }
    state.classpathTargetSpecificBit?.let { setTargetSpecificData(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, it) }
    state.agentTargetSpecificBit?.let { setTargetSpecificData(JavaLanguageRuntimeType.AGENTS_VOLUME, it) }
  }

  private fun MyState.saveInState(volumeDescriptor: VolumeDescriptor, doSave: MyState.(String?, BaseState?) -> Unit) {
    val path = getTargetPathValue(volumeDescriptor)
    val bit = getTargetSpecificData(volumeDescriptor)
    doSave(path, bit)
  }

  class MyState : BaseState() {
    var homePath by string()
    var javaVersionString by string()

    var applicationFolder by string()
    var applicationTargetSpecificBit by property<BaseState>()

    var classpathFolder by string()
    var classpathTargetSpecificBit by property<BaseState>()

    var agentFolder by string()
    var agentTargetSpecificBit by property<BaseState>()
  }
}