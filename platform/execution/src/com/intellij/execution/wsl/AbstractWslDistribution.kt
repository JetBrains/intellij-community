// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * This is a temporary interface used for wsl classes transition. Please, do not use it
 */
interface AbstractWslDistribution {
  /**
   * @return Linux path for a file pointed by `windowsPath` or null if unavailable, like \\MACHINE\path
   */
  @NlsSafe
  fun getWslPath(windowsPath: Path): String?

  /**
   * Patches passed command line to make it runnable in WSL context, e.g changes `date` to `ubuntu run "date"`.
   *
   *
   *
   *
   * Environment variables and working directory are mapped to the chain calls: working dir using `cd` and environment variables using `export`,
   * e.g `bash -c "export var1=val1 && export var2=val2 && cd /some/working/dir && date"`.
   *
   *
   *
   *
   * Method should properly handle quotation and escaping of the environment variables.
   *
   *
   *
   * @param commandLine command line to patch
   * @param project     current project
   * @param options     [WSLCommandLineOptions] instance
   * @param <T>         GeneralCommandLine or descendant
   * @return original `commandLine`, prepared to run in WSL context
  </T> */
  @Throws(ExecutionException::class)
  fun <T : GeneralCommandLine> patchCommandLine(commandLine: T,
                                                project: Project?,
                                                options: WSLCommandLineOptions): T

  /**
   * @return UNC root for the distribution, e.g. `\\wsl$\Ubuntu`
   */
  @ApiStatus.Experimental
  fun getUNCRootPath(): Path

  /**
   * Unique distrib id
   */
  @get:NlsSafe
  val msId: String

}

/**
 * throws exception instead of null
 */
fun AbstractWslDistribution.getWslPathSafe(path: Path): String =
  getWslPath(path) ?: throw Exception("Can't access from Linux: $path")

/**
 * How Linux can access tool from IJ "bin" folder
 */
fun AbstractWslDistribution.getToolLinuxPath(toolName: String): String =
  testOverrideWslToolRoot?.plus("/$toolName")
  ?: getWslPathSafe(PathManager.findBinFileWithException(toolName))

private var testOverrideWslToolRoot: String? = null

@TestOnly
fun testOverrideWslToolRoot(linuxPath: String, disposable: Disposable) {
  require(!linuxPath.endsWith("/"))
  Disposer.register(disposable) { testOverrideWslToolRoot = null }
  testOverrideWslToolRoot = linuxPath
}