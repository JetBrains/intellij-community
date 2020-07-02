// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.CompletableFuture

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
    if (config.homePath.isNotBlank() && config.javaVersionString.isNotBlank()) return null

    return object : Introspector {
      override fun introspect(subject: Introspectable): CompletableFuture<JavaLanguageRuntimeConfiguration>? {
        if (config.homePath.isBlank()) {
          val home = subject.getEnvironmentVariable("JAVA_HOME")
          home?.let { config.homePath = home }
        }

        if (config.javaVersionString.isNotBlank()) {
          return CompletableFuture.completedFuture(config)
        }

        return subject.promiseExecuteScript("java -version")
          .thenApply { output ->
            output?.let { StringUtil.splitByLines(output, true) }
              ?.firstOrNull()
              ?.let { JavaVersion.parse(it) }
              ?.let { config.javaVersionString = it.toString() }
            return@thenApply config
          }
      }
    }
  }

  override fun volumeDescriptors(config: JavaLanguageRuntimeConfiguration) = listOf(APPLICATION_FOLDER_VOLUME,
                                                                                    CLASS_PATH_VOLUME,
                                                                                    AGENTS_VOLUME)

  companion object {
    @JvmStatic
    val TYPE_ID = "JavaLanguageRuntime"

    @JvmStatic
    val APPLICATION_FOLDER_VOLUME =
      VolumeDescriptor(ExecutionBundle.message("java.language.runtime.application.folder.label"),
                       ExecutionBundle.message("java.language.runtime.application.folder.description"),
                       "/app")

    @JvmStatic
    val CLASS_PATH_VOLUME =
      VolumeDescriptor(ExecutionBundle.message("java.language.runtime.classpath.volume.label"),
                       ExecutionBundle.message("java.language.runtime.classpath.volume.description"),
                       "")

    @JvmStatic
    val AGENTS_VOLUME =
      VolumeDescriptor(ExecutionBundle.message("java.language.runtime.agents.volume.label"),
                       ExecutionBundle.message("java.language.runtime.application.folder.description"),
                       "")
  }
}