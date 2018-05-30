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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

object ExecUtil {

  private val hasGkSudo = PathExecLazyValue("gksudo")
  private val hasKdeSudo = PathExecLazyValue("kdesudo")
  private val hasPkExec = PathExecLazyValue("pkexec")
  private val hasGnomeTerminal = PathExecLazyValue("gnome-terminal")
  private val hasKdeTerminal = PathExecLazyValue("konsole")
  private val hasXTerm = PathExecLazyValue("xterm")

  @JvmStatic
  val osascriptPath: String
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: String
    get() = "/usr/bin/open"

  @JvmStatic
  val windowsShellName: String
    get() = CommandLineUtil.getWinShellName()

  @Throws(IOException::class)
  @JvmStatic
  fun loadTemplate(loader: ClassLoader, templateName: String, variables: Map<String, String>?): String {
    val stream = loader.getResourceAsStream(templateName) ?: throw IOException("Template '$templateName' not found by $loader")

    val template = FileUtil.loadTextAndClose(InputStreamReader(stream, StandardCharsets.UTF_8))
    if (variables == null || variables.size == 0) {
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

  @Throws(IOException::class, ExecutionException::class)
  @JvmStatic
  fun createTempExecutableScript(prefix: String, suffix: String, content: String): File {
    val tempDir = File(PathManager.getTempPath())
    val tempFile = FileUtil.createTempFile(tempDir, prefix, suffix, true, true)
    FileUtil.writeToFile(tempFile, content.toByteArray(StandardCharsets.UTF_8))
    if (!tempFile.setExecutable(true, true)) {
      throw ExecutionException("Failed to make temp file executable: $tempFile")
    }
    return tempFile
  }

  @Throws(ExecutionException::class)
  @JvmStatic
  fun execAndGetOutput(commandLine: GeneralCommandLine): ProcessOutput {
    return CapturingProcessHandler(commandLine).runProcess()
  }

  @Throws(ExecutionException::class)
  @JvmStatic
  fun execAndGetOutput(commandLine: GeneralCommandLine, timeoutInMilliseconds: Int): ProcessOutput {
    return CapturingProcessHandler(commandLine).runProcess(timeoutInMilliseconds)
  }

  @JvmStatic
  fun execAndReadLine(commandLine: GeneralCommandLine): String? {
    try {
      return readFirstLine(commandLine.createProcess().inputStream, commandLine.charset)
    }
    catch (e: ExecutionException) {
      Logger.getInstance("com.intellij.execution.util.ExecUtil").debug(e)
      return null
    }

  }

  @JvmStatic
  fun readFirstLine(stream: InputStream, cs: Charset?): String? {
    try {
      BufferedReader(
        if (cs == null) InputStreamReader(stream) else InputStreamReader(stream, cs)).use { reader -> return reader.readLine() }
    }
    catch (e: IOException) {
      Logger.getInstance("com.intellij.execution.util.ExecUtil").debug(e)
      return null
    }

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
  @Throws(ExecutionException::class, IOException::class)
  @JvmStatic
  fun sudo(commandLine: GeneralCommandLine, prompt: String): Process {
    return sudoCommand(commandLine, prompt).createProcess()
  }

  @Throws(ExecutionException::class, IOException::class)
  private fun sudoCommand(commandLine: GeneralCommandLine, prompt: String): GeneralCommandLine {
    if (SystemInfo.isUnix && "root" == System.getenv("USER")) {
      return commandLine
    }

    val command = ContainerUtil.newArrayList<String>()
    command.add(commandLine.exePath)
    command.addAll(commandLine.parametersList.list)

    val sudoCommandLine: GeneralCommandLine
    if (SystemInfo.isWinVistaOrNewer) {
      // launcher.exe process with elevated permissions on UAC.
      val launcherExe = PathManager.findBinFileWithException("launcher.exe")
      sudoCommandLine = GeneralCommandLine(launcherExe.path)
      sudoCommandLine.setWorkDirectory(commandLine.workDirectory)
      sudoCommandLine.addParameter(commandLine.exePath)
      sudoCommandLine.addParameters(commandLine.parametersList.parameters)
      sudoCommandLine.environment.putAll(commandLine.effectiveEnvironment)
    }
    else if (SystemInfo.isMac) {
      val escapedCommandLine = StringUtil.join(command, { escapeAppleScriptArgument(it) }, " & \" \" & ")
      val escapedScript = "tell current application\n" +
                          "   activate\n" +
                          "   do shell script " + escapedCommandLine + " with administrator privileges without altering line endings\n" +
                          "end tell"
      sudoCommandLine = GeneralCommandLine(osascriptPath, "-e", escapedScript)
    }
    else if (hasGkSudo.value) {
      val sudoCommand = ContainerUtil.newArrayList<String>()
      sudoCommand.addAll(Arrays.asList("gksudo", "--message", prompt, "--"))
      sudoCommand.addAll(command)
      sudoCommandLine = GeneralCommandLine(sudoCommand)
    }
    else if (hasKdeSudo.value) {
      val sudoCommand = ContainerUtil.newArrayList<String>()
      sudoCommand.addAll(Arrays.asList("kdesudo", "--comment", prompt, "--"))
      sudoCommand.addAll(command)
      sudoCommandLine = GeneralCommandLine(sudoCommand)
    }
    else if (hasPkExec.value) {
      command.add(0, "pkexec")
      sudoCommandLine = GeneralCommandLine(command)
    }
    else if (SystemInfo.isUnix && hasTerminalApp()) {
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
      sudoCommandLine = GeneralCommandLine(getTerminalCommand("Install", script.absolutePath))
    }
    else {
      throw UnsupportedOperationException("Unsupported OS/desktop: " + SystemInfo.OS_NAME + '/'.toString() + SystemInfo.SUN_DESKTOP)
    }

    return sudoCommandLine
      .withWorkDirectory(commandLine.workDirectory)
      .withEnvironment(commandLine.environment)
      .withParentEnvironmentType(commandLine.parentEnvironmentType)
      .withRedirectErrorStream(commandLine.isRedirectErrorStream)
  }

  @Throws(IOException::class, ExecutionException::class)
  @JvmStatic
  fun sudoAndGetOutput(commandLine: GeneralCommandLine, prompt: String): ProcessOutput {
    return execAndGetOutput(sudoCommand(commandLine, prompt))
  }

  private fun escapeAppleScriptArgument(arg: String): String {
    return "quoted form of \"" + arg.replace("\"", "\\\"") + "\""
  }

  @JvmStatic
  fun escapeUnixShellArgument(arg: String): String {
    return "'" + arg.replace("'", "'\"'\"'") + "'"
  }

  @JvmStatic
  fun hasTerminalApp(): Boolean {
    return SystemInfo.isWindows || SystemInfo.isMac || hasKdeTerminal.value || hasGnomeTerminal.value || hasXTerm.value
  }

  @JvmStatic
  fun getTerminalCommand(title: String?, command: String): List<String> {
    var title = title
    if (SystemInfo.isWindows) {
      title = title?.replace('"', '\'') ?: ""
      return Arrays.asList(windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(title), command)
    }
    else if (SystemInfo.isMac) {
      return Arrays.asList(openCommandPath, "-a", "Terminal", command)
    }
    else if (hasKdeTerminal.value) {
      return if (title != null)
        Arrays.asList("konsole", "-p", "tabtitle=\"" + title.replace('"', '\'') + "\"", "-e", command)
      else
        Arrays.asList("konsole", "-e", command)
    }
    else if (hasGnomeTerminal.value) {
      return if (title != null)
        Arrays.asList("gnome-terminal", "-t", title, "-x", command)
      else
        Arrays.asList("gnome-terminal", "-x", command)
    }
    else if (hasXTerm.value) {
      return if (title != null)
        Arrays.asList("xterm", "-T", title, "-e", command)
      else
        Arrays.asList("xterm", "-e", command)
    }

    throw UnsupportedOperationException("Unsupported OS/desktop: " + SystemInfo.OS_NAME + '/'.toString() + SystemInfo.SUN_DESKTOP)
  }

  //<editor-fold desc="Deprecated stuff.">

  @Deprecated("use {@code new GeneralCommandLine(command).createProcess().waitFor()} (to be removed in IDEA 16) ")
  @Throws(ExecutionException::class, InterruptedException::class)
  @JvmStatic
  fun execAndGetResult(vararg command: String): Int {
    assert(command != null && command.size > 0)
    return GeneralCommandLine(*command).createProcess().waitFor()
  }


  @Deprecated("use {@code new GeneralCommandLine(command).createProcess().waitFor()} (to be removed in IDEA 16) ")
  @Throws(ExecutionException::class, InterruptedException::class)
  @JvmStatic
  fun execAndGetResult(command: List<String>): Int {
    return GeneralCommandLine(command).createProcess().waitFor()
  }


  @Deprecated("use {@link #execAndGetOutput(GeneralCommandLine)} instead (to be removed in IDEA 16) ")
  @Throws(ExecutionException::class)
  @JvmStatic
  fun execAndGetOutput(command: List<String>, workDir: String?): ProcessOutput {
    val commandLine = GeneralCommandLine(command).withWorkDirectory(workDir)
    return CapturingProcessHandler(commandLine).runProcess()
  }


  @Deprecated("use {@link #execAndReadLine(GeneralCommandLine)} instead (to be removed in IDEA 16) ")
  @JvmStatic
  fun execAndReadLine(vararg command: String): String? {
    return execAndReadLine(GeneralCommandLine(*command))
  }


  @Deprecated("use {@link #execAndReadLine(GeneralCommandLine)} instead (to be removed in IDEA 16) ")
  @JvmStatic
  fun execAndReadLine(charset: Charset?, vararg command: String): String? {
    var commandLine = GeneralCommandLine(*command)
    if (charset != null) commandLine = commandLine.withCharset(charset)
    return execAndReadLine(commandLine)
  }
  //</editor-fold>
}