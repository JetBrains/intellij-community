// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Platform
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.util.text.StringUtil

internal class JdkCommandLineSetup(private val request: TargetEnvironmentRequest,
                                   private val target: TargetEnvironmentConfiguration?) {

  val commandLine = TargetedCommandLineBuilder(request)
  private val javaConfiguration: JavaLanguageRuntimeConfiguration? = target?.runtimes?.findByType(
    JavaLanguageRuntimeConfiguration::class.java)

  fun setup(javaParameters: SimpleJavaParameters) {
    setupExePath(javaParameters)
    JdkUtil.setupCommandLine(commandLine, request, javaParameters, javaConfiguration)
  }

  @Throws(CantRunException::class)
  private fun setupExePath(javaParameters: SimpleJavaParameters) {
    if (request is LocalTargetEnvironmentRequest || target == null) {
      val jdk = javaParameters.jdk ?: throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val type = jdk.sdkType
      if (type !is JavaSdkType) throw CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"))
      val exePath = (type as JavaSdkType).getVMExecutablePath(jdk)
                    ?: throw CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"))
      commandLine.setExePath(exePath)
    }
    else {
      if (javaConfiguration == null) {
        throw CantRunException("Cannot find Java configuration in " + target.displayName + " target")
      }

      val java = if (platform == Platform.WINDOWS) "java.exe" else "java"
      commandLine.setExePath(joinPath(arrayOf(javaConfiguration.homePath, "bin", java)))
    }
  }

  private val platform = request.targetPlatform.platform
  private fun joinPath(segments: Array<String>) = StringUtil.join(segments, platform.fileSeparator.toString())
}