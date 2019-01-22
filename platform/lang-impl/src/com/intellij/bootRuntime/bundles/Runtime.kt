// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.bundles

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import java.io.File

enum class BundleSource {
  STANDARD,
  BINTRAY,
  BUNDLED,
  INSTALLED,
  UNKNOWN;

  fun getRepresentaton(): String {
    return when {
      this == STANDARD -> "St"
      this == BINTRAY -> "Bt"
      this == BUNDLED -> "Bd"
      this == INSTALLED -> "Inst"
      else -> "Unknown"
    }
  }
}

abstract class Runtime(initialLocation:File) {


  open val fileName: String by lazy {
    initialLocation.name
  }

  open val installationPath: File by lazy {
    //todo change the filename to a composite name from version
    File(PathManager.getConfigPath() + File.separator + "jdks" + File.separator + fileName)
  }

  val transitionPath: File by lazy {
    File(PathManager.getPluginTempPath(), fileName)
  }

  val downloadPath: File by lazy {
    File(PathManager.getPluginTempPath(), fileName)
  }

  abstract fun install()

  override fun toString(): String {
    return fileName
  }

  protected fun fetchVersion (): String {
    val javaFile = installationPath.walk().filter { file -> file.nameWithoutExtension == "java" }.first()
    try {
      val output = ExecUtil.execAndGetOutput(GeneralCommandLine(javaFile.path, "-version"))
      val matchResult: MatchResult? = "version \"(\\d?(.\\d)*(.\\d)*_*\\d*\\d*-*(ea|release|internal)*)\"".toRegex().find(output.stderr)
      return matchResult?.groups?.get(1)?.value.orEmpty()
    }
    catch (e: ExecutionException) {
      print("Error: ${e}")
    }
    return "Undefined"
  }
}