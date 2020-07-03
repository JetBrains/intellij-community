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

    it.saveInState(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME) { volumeState ->
      applicationFolder = volumeState
    }
    it.saveInState(JavaLanguageRuntimeType.CLASS_PATH_VOLUME) { volumeState ->
      classpathFolder = volumeState
    }
    it.saveInState(JavaLanguageRuntimeType.AGENTS_VOLUME) { volumeState ->
      agentFolder = volumeState
    }
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.javaVersionString = state.javaVersionString ?: ""

    loadVolumeState(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME, state.applicationFolder)
    loadVolumeState(JavaLanguageRuntimeType.CLASS_PATH_VOLUME, state.classpathFolder)
    loadVolumeState(JavaLanguageRuntimeType.AGENTS_VOLUME, state.agentFolder)
  }

  private fun MyState.saveInState(volumeDescriptor: VolumeDescriptor, doSave: MyState.(VolumeState?) -> Unit) {
    val volumeState = VolumeState().also {
      it.remotePath = getTargetPathValue(volumeDescriptor)
      it.targetSpecificData = getTargetSpecificData(volumeDescriptor)
    }
    doSave(volumeState)
  }

  private fun loadVolumeState(volumeDescriptor: VolumeDescriptor, volumeState: VolumeState?) {
    volumeState?.let {
      setTargetPath(volumeDescriptor, it.remotePath)
      setTargetSpecificData(volumeDescriptor, it.targetSpecificData)
    }
  }

  class MyState : BaseState() {
    var homePath by string()
    var javaVersionString by string()

    var applicationFolder by property<VolumeState>()
    var classpathFolder by property<VolumeState>()
    var agentFolder by property<VolumeState>()
  }
}