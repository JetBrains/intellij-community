// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object ExecUtil {
  private val hasGkSudo = PathExecLazyValue("gksudo")
  private val hasKdeSudo = PathExecLazyValue("kdesudo")
  private val hasPkExec = PathExecLazyValue("pkexec")
  private val hasGnomeTerminal = PathExecLazyValue("gnome-terminal")
  private val hasKdeTerminal = PathExecLazyValue("konsole")
  private val hasXTerm = PathExecLazyValue("xterm")

  private const val nicePath = "/usr/bin/nice"
  private val hasNice by lazy { File(nicePath).exists() }

  @JvmStatic
  val osascriptPath: String
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: String
    get() = "/usr/bin/open"

  @JvmStatic
  val windowsShellName: String
    get() = CommandLineUtil.getWinShellName()

  @JvmStatic
  @Throws(IOException::class)
  fun loadTemplate(loader: ClassLoader, templateName: String, variables: Map<String, String>?): String {
    val stream = loader.getResourceAsStream(templateName) ?: throw IOException("Template '$templateName' not found by $loader")

    val template = FileUtil.loadTextAndClose(InputStreamReader(stream, StandardCharsets.UTF_8))
    if (variables == null || variables.isEmpty()) {
      return template
    }

    val buffer = StringBuilder(template)
    for ((name, value) in variables) {
      val pos = buffer.indexOf(name)
      if (pos >= 0) {
        buffer.replace(pos, pos + name.length, value)
      }
    }
    return buffer.toString()
  }

  @JvmStatic
  @Throws(IOException::class, ExecutionException::class)
  fun createTempExecutableScript(prefix: String, suffix: String, content: String): File {
    val tempDir = File(PathManager.getTempPath())
    val tempFile = FileUtil.createTempFile(tempDir, prefix, suffix, true, true)
    FileUtil.writeToFile(tempFile, content.toByteArray(StandardCharsets.UTF_8))
    if (!tempFile.setExecutable(true, true)) {
      throw ExecutionException("Failed to make temp file executable: $tempFile")
    }
    return tempFile
  }

  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(commandLine: GeneralCommandLine): ProcessOutput =
    CapturingProcessHandler(commandLine).runProcess()

  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(commandLine: GeneralCommandLine, timeoutInMilliseconds: Int): ProcessOutput =
    CapturingProcessHandler(commandLine).runProcess(timeoutInMilliseconds)

  @JvmStatic
  fun execAndReadLine(commandLine: GeneralCommandLine): String? = try {
    readFirstLine(commandLine.createProcess().inputStream, commandLine.charset)
  }
  catch (e: ExecutionException) {
    Logger.getInstance(ExecUtil::class.java).debug(e)
    null
  }

  @JvmStatic
  fun readFirstLine(stream: InputStream, cs: Charset?): String? = try {
    BufferedReader(if (cs == null) InputStreamReader(stream) else InputStreamReader(stream, cs)).use { it.readLine() }
  }
  catch (e: IOException) {
    Logger.getInstance(ExecUtil::class.java).debug(e)
    null
  }

  /**
   * Run the command with superuser privileges using safe escaping and quoting.
   *
   * No shell substitutions, input/output redirects, etc. in the command are applied.
   *
   * @param commandLine the command line to execute
   * @param prompt the prompt string for the users
   * @return the results of running the process
   */
  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudo(commandLine: GeneralCommandLine, prompt: String): Process =
    sudoCommand(commandLine, prompt).createProcess()

  @Throws(ExecutionException::class, IOException::class)
  private fun sudoCommand(commandLine: GeneralCommandLine, prompt: String): GeneralCommandLine {
    if (SystemInfo.isUnix && "root" == System.getenv("USER")) {
      return commandLine
    }

    val command = mutableListOf(commandLine.exePath)
    command += commandLine.parametersList.list

    val sudoCommandLine = when {
      SystemInfo.isWinVistaOrNewer -> {
        val launcherExe = PathManager.findBinFileWithException("launcher.exe")
        GeneralCommandLine(listOf(launcherExe.path, commandLine.exePath) + commandLine.parametersList.parameters)
      }
      SystemInfo.isMac -> {
        val escapedCommand = StringUtil.join(command, { escapeAppleScriptArgument(it) }, " & \" \" & ")
        val escapedScript =
          "tell current application\n" +
          "   activate\n" +
          "   do shell script " + escapedCommand + " with administrator privileges without altering line endings\n" +
          "end tell"
        GeneralCommandLine(osascriptPath, "-e", escapedScript)
      }
      hasGkSudo.value -> {
        GeneralCommandLine(listOf("gksudo", "--message", prompt, "--") + command)
      }
      hasKdeSudo.value -> {
        GeneralCommandLine(listOf("kdesudo", "--comment", prompt, "--") + command)
      }
      hasPkExec.value -> {
        command.add(0, "pkexec")
        GeneralCommandLine(command)
      }
      SystemInfo.isUnix && hasTerminalApp() -> {
        val escapedCommandLine = StringUtil.join(command, { escapeUnixShellArgument(it) }, " ")
        val script = createTempExecutableScript(
          "sudo", ".sh",
          "#!/bin/sh\n" +
          "echo " + escapeUnixShellArgument(prompt) + "\n" +
          "echo\n" +
          "sudo -- " + escapedCommandLine + "\n" +
          "STATUS=$?\n" +
          "echo\n" +
          "read -p \"Press Enter to close this window...\" TEMP\n" +
          "exit \$STATUS\n")
        GeneralCommandLine(getTerminalCommand("Install", script.absolutePath))
      }
      else -> {
        throw UnsupportedOperationException("Unsupported OS/desktop: ${SystemInfo.OS_NAME}/${SystemInfo.SUN_DESKTOP}")
      }
    }

    return sudoCommandLine
      .withWorkDirectory(commandLine.workDirectory)
      .withEnvironment(commandLine.environment)
      .withParentEnvironmentType(commandLine.parentEnvironmentType)
      .withRedirectErrorStream(commandLine.isRedirectErrorStream)
  }

  @JvmStatic
  @Throws(IOException::class, ExecutionException::class)
  fun sudoAndGetOutput(commandLine: GeneralCommandLine, prompt: String): ProcessOutput =
    execAndGetOutput(sudoCommand(commandLine, prompt))

  private fun escapeAppleScriptArgument(arg: String) = "quoted form of \"${arg.replace("\"", "\\\"")}\""

  @JvmStatic
  fun escapeUnixShellArgument(arg: String): String = "'${arg.replace("'", "'\"'\"'")}'"

  @JvmStatic
  fun hasTerminalApp(): Boolean =
    SystemInfo.isWindows || SystemInfo.isMac || hasKdeTerminal.value || hasGnomeTerminal.value || hasXTerm.value

  @JvmStatic
  fun getTerminalCommand(title: String?, command: String): List<String> = when {
    SystemInfo.isWindows -> {
      listOf(windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(title?.replace('"', '\'') ?: ""), command)
    }
    SystemInfo.isMac -> {
      listOf(openCommandPath, "-a", "Terminal", command)
    }
    hasKdeTerminal.value -> {
      if (title != null) listOf("konsole", "-p", "tabtitle=\"${title.replace('"', '\'')}\"", "-e", command)
      else listOf("konsole", "-e", command)
    }
    hasGnomeTerminal.value -> {
      if (title != null) listOf("gnome-terminal", "-t", title, "-x", command)
      else listOf("gnome-terminal", "-x", command)
    }
    hasXTerm.value -> {
      if (title != null) listOf("xterm", "-T", title, "-e", command)
      else listOf("xterm", "-e", command)
    }
    else -> {
      throw UnsupportedOperationException("Unsupported OS/desktop: ${SystemInfo.OS_NAME}/${SystemInfo.SUN_DESKTOP}")
    }
  }

  @JvmStatic
  fun setupLowPriorityExecution(commandLine: GeneralCommandLine) {
    if (canRunLowPriority()) {
      val executablePath = commandLine.exePath
      if (SystemInfo.isWindows) {
        commandLine.exePath = windowsShellName
        commandLine.parametersList.prependAll("/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""), executablePath)
      }
      else {
        commandLine.exePath = nicePath
        commandLine.parametersList.prependAll("-n", "10", executablePath)
      }
    }
  }

  private fun canRunLowPriority() = Registry.`is`("ide.allow.low.priority.process") && (SystemInfo.isWindows || hasNice)

  //<editor-fold desc="Deprecated stuff.">

  @Deprecated("use {@link #execAndGetOutput(GeneralCommandLine)} instead (to be removed in IDEA 2019) ")
  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(command: List<String>, workDir: String?): ProcessOutput {
    val commandLine = GeneralCommandLine(command).withWorkDirectory(workDir)
    return CapturingProcessHandler(commandLine).runProcess()
  }

  //</editor-fold>
}