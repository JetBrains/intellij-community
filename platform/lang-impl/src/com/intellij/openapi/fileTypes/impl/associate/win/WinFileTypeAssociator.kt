// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.win

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.system.CpuArch
import java.nio.file.Path

class WinFileTypeAssociator : com.intellij.openapi.fileTypes.impl.associate.SystemFileTypeAssociator {

  /**
   * Associates given file types with IDE using
   * [ftype](https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/ftype) and
   * [assoc](https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/assoc) commands:
   * ```
   *   ftype <IDE name>=<IDE launch command line> "%1" %*
   *   assoc <.extension1>=<IDE name>
   *   assoc <.extension2>=<IDE name>
   *   ...
   *   assoc <.extensionN>=<IDE name>
   * ```
   * Since `ftype` and `assoc` are internal commands of Windows CMD shell (cmd.exe), they are run as `cmd.exe /c <...>`.
   */
  @Throws(OSFileAssociationException::class)
  override fun associateFileTypes(fileTypes: List<FileType>) {
    val extensions: List<Extension> = fileTypes.map { OSAssociateFileTypesUtil.getMatchers(it) }.flatten()
      .filterIsInstance(ExtensionFileNameMatcher::class.java)
      .map { Extension(it.extension) }
    runCmdCommandWithSudo(createCmdScriptText(extensions)) {
      ApplicationBundle.message("light.edit.associate.fileTypes.error.message", ApplicationNamesInfo.getInstance().fullProductName)
    }
  }

  private fun createCmdScriptText(extensions: List<Extension>): String {
    val assocCommands = extensions.map { "assoc ${it.dotExtension()}=${getUniqueFileType()}" }
    val allCommands = listOf(createAssignCommandLineToFiletypeCommand()) + assocCommands
    return allCommands.joinToString(" && ") { "($it)" }
  }

  /**
   */
  @Throws(OSFileAssociationException::class)
  private fun createAssignCommandLineToFiletypeCommand(): String {
    val scriptName = ApplicationNamesInfo.getInstance().scriptName
    val suffix = if (CpuArch.isIntel64()) "64" else ""
    val scriptPath: Path = PathManager.findBinFile("$scriptName$suffix.exe") ?: throw OSFileAssociationException(
      ApplicationBundle.message("desktop.entry.script.missing", PathManager.getBinPath()))
    return "ftype " + getUniqueFileType() + "=" + StringUtil.wrapWithDoubleQuote(scriptPath.toString()) + " \"%1\" %*"
  }

  private fun runCmdCommandWithSudo(cmdCommand: String, errorMessageProvider: () -> String) {
    val commandLine = GeneralCommandLine("cmd.exe", "/d", "/c", GeneralCommandLine.inescapableQuote(cmdCommand))
    try {
      val sudoCommandLine = ExecUtil.sudoCommand(commandLine, "")
      val processOutput = ExecUtil.execAndGetOutput(sudoCommandLine, 30000)
      if (processOutput.exitCode != 0 || processOutput.isCancelled || processOutput.isTimeout) {
        throw OSFileAssociationException(errorMessageProvider(), Exception(
          stringify(sudoCommandLine, processOutput)))
      }
    }
    catch (e: Exception) {
      throw OSFileAssociationException(errorMessageProvider(), Exception(
        stringify(commandLine, null), e))
    }
  }

  private fun stringify(commandLine: GeneralCommandLine, processOutput: ProcessOutput?) : String {
    var result = listOf("command line: " + commandLine.commandLineString,
                        "working directory: " + commandLine.workDirectory)
    if (processOutput != null) {
      result = result + listOf(
        "exit code: " + processOutput.exitCode,
        "timeout: " + processOutput.isTimeout,
        "cancelled: " + processOutput.isCancelled,
        "stdout: " + processOutput.stdout,
        "stderr: " + processOutput.stderr
      )
    }
    return result.joinToString("\n")
  }

  private fun getUniqueFileType() = ApplicationNamesInfo.getInstance().fullProductName.replace(" ", "_")

  private data class Extension(private val extension: String) {
    fun dotExtension(): String = if (extension.startsWith(".")) extension else ".$extension"
  }
}
