// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.EnvFilesOptions
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.envFile.parseEnvFile
import com.intellij.execution.util.ProgramParametersConfigurator.ParametersConfiguratorException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.withPushPop
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.EnvReader
import com.intellij.util.PathUtilRt
import org.jetbrains.annotations.ApiStatus.Experimental
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import java.util.*

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
      val extension = FileUtilRt.getExtension(path).lowercase()
      if (extension == "sh" || extension == "bat") {
        if (parse) {
          val indicator = ProgressManager.getGlobalProgressIndicator()
          indicator.withPushPop {
            indicator.text = ExecutionBundle.message("progress.title.script.running", PathUtilRt.getFileName(path))
            result.putAll(launchScript(path, extension))
          }
        }
      }
      else {
        val text = FileUtil.loadFile(File(path))
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

private fun launchScript(path: String, extension: String): MutableMap<String, String> {
  val envReader = EnvReader()
  when (extension) {
    "bat" -> return envReader.readBatEnv(Path.of(path), null)
    else -> return envReader.readShellEnv(Path.of(path), null)
  }
}