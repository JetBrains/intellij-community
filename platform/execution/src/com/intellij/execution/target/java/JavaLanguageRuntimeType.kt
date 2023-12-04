// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.lang.JavaVersion
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.Icon

class JavaLanguageRuntimeType : LanguageRuntimeType<JavaLanguageRuntimeConfiguration>(JavaLanguageRuntimeTypeConstants.TYPE_ID) {
  override val icon: Icon = AllIcons.FileTypes.Java

  @NlsSafe
  override val displayName: String = "Java"

  @Nls
  override val configurableDescription: @Nls String =
    ExecutionBundle.message("JavaLanguageRuntimeType.configurable.description.configure.java")

  @Nls
  override val launchDescription: @Nls String = ExecutionBundle.message("JavaLanguageRuntimeType.launch.description.run.java.application")

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean = true

  override fun createDefaultConfig(): JavaLanguageRuntimeConfiguration = JavaLanguageRuntimeConfiguration()

  override fun createSerializer(config: JavaLanguageRuntimeConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project,
                                  config: JavaLanguageRuntimeConfiguration,
                                  targetEnvironmentType: TargetEnvironmentType<*>,
                                  targetSupplier: Supplier<TargetEnvironmentConfiguration>): Configurable {
    return ApplicationManager.getApplication().getService(JavaLanguageRuntimeUIFactory::class.java).create(config, targetEnvironmentType,
                                                                                                           targetSupplier, project)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): JavaLanguageRuntimeConfiguration? {
    return target.runtimes.findByType()
  }

  override fun createIntrospector(config: JavaLanguageRuntimeConfiguration): Introspector<JavaLanguageRuntimeConfiguration>? {
    if (config.homePath.isNotBlank() && config.javaVersionString.isNotBlank()) return null

    return object : Introspector<JavaLanguageRuntimeConfiguration> {
      override fun introspect(subject: Introspectable): CompletableFuture<JavaLanguageRuntimeConfiguration> {
        val javaHomePromise = if (config.homePath.isBlank()) {
          subject.promiseEnvironmentVariable("JAVA_HOME")
            .thenApply { acceptJavaHome(it) }
        }
        else {
          Introspector.DONE
        }

        val versionPromise = if (config.javaVersionString.isBlank()) {
          subject.promiseExecuteScript(listOf("java", "-version"))
            .thenApply { it.acceptJavaVersion() }
        }
        else {
          Introspector.DONE
        }

        return CompletableFuture.allOf(javaHomePromise, versionPromise)
          .thenApply {
            config
          }
      }

      private fun acceptJavaHome(javaHome: String?) {
        if (config.homePath.isBlank()) {
          javaHome.nullize(true)?.let {
            config.homePath = it
          }
        }
      }

      private fun ProcessOutput.acceptJavaVersion() {
        listOf(stderr, stdout)
          .firstNotNullOfOrNull { tryParseJavaVersionFromOutput(it) }
          ?.let { config.javaVersionString = it.toString() }
      }
    }
  }

  private fun tryParseJavaVersionFromOutput(output: String?): JavaVersion? =
    output?.lines()?.firstNotNullOfOrNull {
      kotlin.runCatching { JavaVersion.parse(it) }.getOrNull()
    }

  override fun volumeDescriptors(): List<VolumeDescriptor> = listOf(JavaLanguageRuntimeTypeConstants.CLASS_PATH_VOLUME, JavaLanguageRuntimeTypeConstants.AGENTS_VOLUME)

  override fun duplicateConfig(config: JavaLanguageRuntimeConfiguration): JavaLanguageRuntimeConfiguration =
    duplicatePersistentComponent(this, config)
}

object JavaLanguageRuntimeTypeConstants {
  @JvmStatic
  val TYPE_ID: String = "JavaLanguageRuntime"

  @JvmStatic
  val CLASS_PATH_VOLUME: VolumeDescriptor = VolumeDescriptor(
    JavaLanguageRuntimeType::class.qualifiedName + ":classPath",
    ExecutionBundle.message("java.language.runtime.classpath.volume.label"),
    ExecutionBundle.message("java.language.runtime.classpath.volume.description"),
    ExecutionBundle.message("java.language.runtime.classpath.volume.browsing.title"),
    ""
  )

  @JvmStatic
  val AGENTS_VOLUME: VolumeDescriptor = VolumeDescriptor(
    JavaLanguageRuntimeType::class.qualifiedName + ":agents",
    ExecutionBundle.message("java.language.runtime.agents.volume.label"),
    ExecutionBundle.message("java.language.runtime.agents.volume.description"),
    ExecutionBundle.message("java.language.runtime.agents.volume.browsing.title"),
    ""
  )
}
