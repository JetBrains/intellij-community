// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class JavaLanguageRuntimeConfiguration : LanguageRuntimeConfiguration(JavaLanguageRuntimeType.TYPE_ID),
                                         PersistentStateComponent<JavaLanguageRuntimeConfiguration.MyState> {
  var homePath: String = ""
  var applicationFolder: String = ""
  var javaVersionString: String = ""

  override fun getState() = MyState().also {
    it.homePath = this.homePath
    it.applicationFolder = this.applicationFolder
    it.javaVersionString = this.javaVersionString
  }

  override fun loadState(state: MyState) {
    this.homePath = state.homePath ?: ""
    this.applicationFolder = state.applicationFolder ?: ""
    this.javaVersionString = state.javaVersionString ?: ""
  }

  class MyState : BaseState() {
    var homePath by string()
    var applicationFolder by string()
    var javaVersionString by string()
  }
}