// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.execution.ConsoleFolding
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.wsl.WSLDistribution.*
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Fold `wsl.exe --distibution ...` line at the beginning of the console output.
 *
 * E.g.:
 * Original command-line:
 *    C:\WINDOWS\system32\wsl.exe --distribution Ubuntu-18.04 --exec /bin/sh -c "export JETBRAINS_IDE=TRUE && cd <working-dir> && <user command>; exitcode=$?; sleep 0.001; (exit $exitcode)"
 * Folded command-line:
 *    wsl.exe... <user command> ...
 *
 * See [com.intellij.execution.wsl.WSLDistribution.patchCommandLine]
 */
@ApiStatus.Internal
class WslDistributionConsoleFolding : ConsoleFolding() {
  override fun shouldFoldLine(project: Project, line: String): Boolean {
    return shouldFoldLineNoProject(line)
  }

  @VisibleForTesting
  fun shouldFoldLineNoProject(line: String): Boolean {
    // check if line contains `wsl.exe --distribution` and it's not at the start of line
    val wslExeIndex = line.indexOf(WSL_EXE_DISTRIBUTION)
    if (wslExeIndex <= 0) {
      return false
    }

    // check if line contains `--exec` and it's located after `wsl.exe --distribution`
    val execIndex = line.indexOf(EXEC_PARAMETER, wslExeIndex + 1)
    if (execIndex < 0) {
      return false
    }

    // check if line contains `&& `. If yes, then it should be located after `--exec`
    val ampIndex = line.indexOf("&& ")
    if (ampIndex in 0 until execIndex) {
      return false
    }

    // check that `wsl.exe` path (usually `C:\WINDOWS\system32`) is contained in `PATH` variable
    val wslExePath = line.substring(0, wslExeIndex).trim('\\', '/')
    return PathEnvironmentVariableUtil.getPathVariableValue()?.contains(wslExePath) == true
  }

  override fun shouldBeAttachedToThePreviousLine(): Boolean = false

  override fun getPlaceholderText(project: Project, lines: MutableList<String>): String? {
    if (lines.size != 1) {
      return null
    }

    return getPlaceholderText(lines[0])
  }

  @VisibleForTesting
  fun getPlaceholderText(line: String): String? {
    // Find WSL distribution name. It is the next parameter after `--distribution `
    val indexOfDistribution = line.indexOf(DISTRIBUTION_PARAMETER) + DISTRIBUTION_PARAMETER.length + 1 // +1 for space after parameter
    if (indexOfDistribution <= DISTRIBUTION_PARAMETER.length || indexOfDistribution >= line.length) {
      return null
    }
    var distributionLine = line.substring(indexOfDistribution)
    val distributionSpaceIndex = distributionLine.indexOf(" ")
    if (distributionSpaceIndex < 0) {
      return null
    }
    distributionLine = distributionLine.substring(0, distributionSpaceIndex)
    distributionLine = distributionLine.trim('"')

    // Find user command-line.
    // It starts either at the last `&& ` or after `--exec`
    // It ends either at the end of line or at the `; exitcode=$?`
    val indexOfAmp = line.lastIndexOf("&& ")
    val indexOfExec = line.indexOf(EXEC_PARAMETER)
    var userCLStart = indexOfAmp + 3
    if (userCLStart <= 2) {
      userCLStart = indexOfExec + EXEC_PARAMETER.length + 1
      if (userCLStart <= EXEC_PARAMETER.length) {
        return null
      }
    }

    val indexOfExitCode = line.lastIndexOf(EXIT_CODE_PARAMETER)
    val endsWithQuote = line.endsWith("\"")
    val userCLEnd = if (indexOfExitCode == -1) {
      // Line can end with quote-sign (e.g. on WSL2 there's no `$exitcode` after user command-line).
      if (endsWithQuote) {
        //|| line.indexOf("$EXEC_PARAMETER -c \"") >= 0
        // Keep the ending quote is line has format `--exec <shell> -c "`.
        // This format is used if [WSLCommandLineOptions.isExecuteCommandInShell] is true
        if (indexOfAmp < 0 && indexOfExec >= 0 && line.indexOf("-c \"") >= 0) {
          line.length -1
        }
        else {
          line.length - 2
        }
      }
      else {
        line.length - 1
      }
    }
    else {
      indexOfExitCode - 1
    }

    val userCL = line.substring(userCLStart..userCLEnd)

    return ExecutionBundle.message("wsl.folding.placeholder", distributionLine, userCL)
  }

  companion object {
    private const val WSL_EXE_DISTRIBUTION = "$WSL_EXE $DISTRIBUTION_PARAMETER"
  }
}