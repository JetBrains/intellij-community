// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.EnvFilesOptions
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.envFile.parseEnvFile
import com.intellij.execution.util.ProgramParametersConfigurator.ParametersConfiguratorException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.withPushPop
import com.intellij.util.ShellEnvironmentReader.*
import org.jetbrains.annotations.ApiStatus.Experimental
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

@Throws(RuntimeConfigurationException::class)
fun checkEnvFiles(configuration: CommonProgramRunConfigurationParameters?) {
  if (configuration is EnvFilesOptions) {
    try {
      configureEnvsFromFiles(configuration as EnvFilesOptions, parse = false)
    }
    catch (e: ParametersConfiguratorException) {
      throw RuntimeConfigurationException(e.message)
    }
  }
}

@Throws(ParametersConfiguratorException::class)
@Experimental
fun configureEnvsFromFiles(configuration: EnvFilesOptions, parse: Boolean = true): Map<String, String> {
  val result: MutableMap<String, String> = HashMap()
  for (path in configuration.envFilePaths) {
    try {
      val file = Path.of(path)
      val extension = file.extension.lowercase()
      if (extension == "sh" || extension == "bat") {
        if (parse) {
          val indicator = ProgressManager.getGlobalProgressIndicator()
          indicator.withPushPop {
            indicator.text = ExecutionBundle.message("progress.title.script.running", file.name)
            val command = when (extension) {
              "bat" -> winShellCommand(file, null)
              else -> shellCommand(null, file, null)
            }
            result.putAll(readEnvironment(command, 0).first)
          }
        }
      }
      else {
        val text = file.readText()
        if (parse) {
          result.putAll(parseEnvFile(text))
        }
      }
    }
    catch (e: FileNotFoundException) {
      throw ParametersConfiguratorException(ExecutionBundle.message("file.not.found.0", path), e)
    }
    catch (e: IOException) {
      throw ParametersConfiguratorException(ExecutionBundle.message("cannot.read.file.0", path), e)
    }
  }
  return result
}
