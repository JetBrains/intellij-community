// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote.java

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.remote.LanguageRuntimeType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

class JavaLanguageRuntimeType : LanguageRuntimeType<JavaLanguageRuntimeConfiguration>(TYPE_ID) {
  override val icon = AllIcons.FileTypes.Java

  override val displayName = "Java"

  override val configurableDescription = "Configure Java"

  override val launchDescription = "Run Java application"

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = JavaLanguageRuntimeConfiguration()

  override fun createSerializer(config: JavaLanguageRuntimeConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project, config: JavaLanguageRuntimeConfiguration): Configurable =
    ServiceManager.getService(JavaLanguageRuntimeUIFactory::class.java).create(config)

  override fun createIntrospector(config: JavaLanguageRuntimeConfiguration): Introspector? {
    if (config.homePath.isNotBlank()) return null

    return object : Introspector {
      override fun introspect(subject: Introspectable) {
        if (config.homePath.isNotBlank()) return // don't want to override user value

        val home = subject.getEnvironmentVariable("JAVA_HOME")
        home?.let { config.homePath = home }
      }
    }
  }

  companion object {
    @JvmStatic
    val TYPE_ID = "JavaLanguageRuntime"
  }
}