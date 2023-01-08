// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle.message
import com.intellij.execution.target.LanguageRuntimeConfigurable
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import java.util.function.Supplier

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration,
                            targetType: TargetEnvironmentType<*>,
                            targetProvider: Supplier<out TargetEnvironmentConfiguration>,
                            project: Project) :
  LanguageRuntimeConfigurable(config, targetType, targetProvider, project) {

  override fun createPanel(): DialogPanel {
    return panel {
      row(message("java.language.runtime.jdk.home.path")) {
        browsableTextField(message("java.language.runtime.jdk.home.path.title"), config::homePath.toMutableProperty())
          .comment(message("java.language.runtime.text.path.to.jdk.on.target"))
      }
      row(message("java.language.runtime.jdk.version")) {
        textField()
          .align(AlignX.FILL)
          .bindText(config::javaVersionString)
      }

      collapsibleGroup(message("java.language.runtime.separator.advanced.volume.settings")) {
        addVolumeUI(JavaLanguageRuntimeType.CLASS_PATH_VOLUME)
        addVolumeUI(JavaLanguageRuntimeType.AGENTS_VOLUME)
      }.topGap(TopGap.NONE)
    }
  }
}