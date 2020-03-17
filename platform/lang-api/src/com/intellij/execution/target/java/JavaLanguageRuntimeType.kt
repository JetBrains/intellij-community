// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.TimeUnit

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
      override fun introspect(subject: Introspectable) {
        if (config.homePath.isBlank()) {
          val home = subject.getEnvironmentVariable("JAVA_HOME")
          home?.let { config.homePath = home }
        }

        if (config.javaVersionString.isBlank()) {
          val promise = subject.promiseExecuteScript("java -version")
            .thenAccept {
              it?.let { StringUtil.splitByLines(it, true) }
                ?.firstOrNull()
                ?.let { JavaVersion.parse(it) }
                ?.let { config.javaVersionString = it.toString() }
            }
          try {
            // todo[remoteServers]: blocking wait
            promise.get(3, TimeUnit.SECONDS)
          }
          catch (e: Exception) {
            LOG.error(e)
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    val TYPE_ID = "JavaLanguageRuntime"
    private val LOG: Logger = Logger.getInstance(JavaLanguageRuntimeType::class.java)
  }
}