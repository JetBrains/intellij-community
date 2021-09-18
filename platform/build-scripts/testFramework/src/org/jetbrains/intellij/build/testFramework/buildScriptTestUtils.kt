// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TestLoggerFactory
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.copyTo

fun createBuildContext(homePath: String, productProperties: ProductProperties,
                       buildTools: ProprietaryBuildTools,
                       skipDependencySetup: Boolean = false,
                       communityHomePath: String = "$homePath/community",
                       buildOptionsCustomizer: (BuildOptions) -> Unit = {},
): BuildContext {
  val options = BuildOptions()
  options.isSkipDependencySetup = skipDependencySetup
  options.isIsTestBuild = true
  options.buildStepsToSkip.add(BuildOptions.getTEAMCITY_ARTIFACTS_PUBLICATION())
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, false).absolutePath
  options.isUseCompiledClassesFromProjectOutput = true
  options.compilationLogEnabled = false
  buildOptionsCustomizer(options)
  return BuildContext.createContext(communityHomePath, homePath, productProperties, buildTools, options)
}

fun runTestBuild(homePath: String, productProperties: ProductProperties, buildTools: ProprietaryBuildTools,
                 communityHomePath: String = "$homePath/community",
                 buildOptionsCustomizer: (BuildOptions) -> Unit = {},
) {
  val buildContext = createBuildContext(homePath, productProperties, buildTools, false, communityHomePath, buildOptionsCustomizer)
  buildContext.messages.debug("Build output root is at ${buildContext.options.outputRootPath}")
  try {
    try {
      BuildTasks.create(buildContext).runTestBuild()
    }
    catch (e: Throwable) {
      try {
        val logFile = (buildContext.messages as BuildMessagesImpl).debugLogFile
        val targetFile = Path(TestLoggerFactory.getTestLogDir(), "${productProperties.baseFileName}-test-build-debug.log")
        logFile.toPath().copyTo(targetFile)
        buildContext.messages.info("Debug log copied to $targetFile")
      }
      catch (copyingException: Throwable) {
        buildContext.messages.info("Failed to copy debug log: ${e.message}")
      }
      throw e
    }
  }
  finally {
    // Redirect debug logging to some other file to prevent locking of output directory on Windows
    val newDebugLog = FileUtil.createTempFile("debug-log-", ".log", true)
    (buildContext.messages as BuildMessagesImpl).setDebugLogPath(newDebugLog.toPath())

    FileUtil.delete(Paths.get(buildContext.options.outputRootPath))
  }
}