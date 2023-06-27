// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script

import com.intellij.ide.CliResult
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * @author gregsh
 */
internal class IdeScriptStarter : ApplicationStarterBase() {
  @Suppress("OVERRIDE_DEPRECATION")
  override val commandName: String
    get() = "ideScript"

  override val usageMessage: String
    get() {
      val scriptName = ApplicationNamesInfo.getInstance().scriptName
      return LangBundle.message("ide.script.starter.usage", scriptName, "ideScript")
    }

  override fun checkArguments(args: List<String>): Boolean = args.size > 1

  override suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    val filePaths = args.subList(1, args.size).map { Path.of(it) }
    val project = guessProject()
    val result = IdeStartupScripts.prepareScriptsAndEngines(filePaths)
    IdeStartupScripts.runAllScriptsImpl(project, result,
                                        if (project == null) redirectStreamsAndGetLogger(result) else logger<IdeScriptStarter>())
    return CliResult.OK
  }
}

private fun guessProject(): Project? {
  val recentFocusedWindow = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
  if (recentFocusedWindow is IdeFrame) {
    return (recentFocusedWindow as IdeFrame).project
  }
  else {
    return ProjectManager.getInstance().openProjects.firstOrNull { o -> o.isInitialized && !o.isDisposed }
  }
}

/** @noinspection UseOfSystemOutOrSystemErr
 */
private fun redirectStreamsAndGetLogger(result: List<Pair<Path, IdeScriptEngine>>): Logger {
  for (pair in result) {
    pair.second.stdOut = OutputStreamWriter(System.out, Charset.defaultCharset())
    pair.second.stdErr = OutputStreamWriter(System.err, Charset.defaultCharset())
    pair.second.stdIn = InputStreamReader(System.`in`, Charset.defaultCharset())
  }
  return object : DefaultLogger(null) {
    override fun info(message: String, t: Throwable?) {
      println("INFO: $message")
    }
  }
}
