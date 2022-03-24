// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle.message
import com.intellij.execution.target.*
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import java.util.function.Supplier

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration,
                            private val targetType: TargetEnvironmentType<*>,
                            private val targetProvider: Supplier<TargetEnvironmentConfiguration>,
                            private val project: Project) :
  LanguageRuntimeConfigurable(config, targetType, targetProvider, project) {

  override fun RowBuilder.addMainPanelUI() {
    row(message("java.language.runtime.jdk.home.path")) {
      val cellBuilder = browsableTextField(message("java.language.runtime.jdk.home.path.title"), config::homePath.toBinding())
      cellBuilder.comment(message("java.language.runtime.text.path.to.jdk.on.target"))
    }
    row(message("java.language.runtime.jdk.version")) {
      textField(config::javaVersionString)
    }
  }

  override fun RowBuilder.addAdditionalPanelUI() {
    addVolumeUI(JavaLanguageRuntimeType.CLASS_PATH_VOLUME)
    addVolumeUI(JavaLanguageRuntimeType.AGENTS_VOLUME)
  }
}