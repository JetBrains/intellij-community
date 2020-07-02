// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration, private val target: TargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  override fun createPanel(): DialogPanel {
    return panel {
      row("JDK home path:") {
        textField(config::homePath)
      }
      row("JDK version:") {
        textField(config::javaVersionString)
      }
    }
  }
}