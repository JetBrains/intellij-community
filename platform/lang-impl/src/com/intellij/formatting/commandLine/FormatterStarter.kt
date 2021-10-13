// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.CodeStyleSettingsLoader
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import kotlin.system.exitProcess


const val FORMAT_COMMAND_NAME = "format"

private val LOG = Logger.getInstance(FormatterStarter::class.java)


/**
 * A launcher class for command-line formatter.
 */
class FormatterStarter : ApplicationStarter {

  val messageOutput = MessageOutput(PrintWriter(System.out), PrintWriter(System.err))

  override fun getCommandName() = FORMAT_COMMAND_NAME

  override fun main(args: Array<String>) {
    messageOutput.info("$appInfo Formatter\n")
    LOG.info(args.joinToString(",", prefix = "Attributes: "))

    try {
      createFormatter(args).use { formatter ->
        formatter.processFiles()
        formatter.printReport()
        if (!formatter.isResultSuccessful()) {
          exitProcess(1)
        }
      }
    }
    catch (e: IOException) {
      throw fatalError(e.localizedMessage)
    }

    (ApplicationManager.getApplication() as ApplicationEx).exit(true, true)
  }

  private fun showUsageAndExit() {
    messageOutput.info(usageInfo)
    exitProcess(0)
  }

  private fun fatalError(message: String): Throwable {
    messageOutput.error("ERROR: $message\n")
    exitProcess(1)
  }

  fun createFormatter(args: Array<String>): FileSetCodeStyleProcessor {
    if (args.size < 2) {
      showUsageAndExit()
    }

    var skipNext = false

    val builder = CodeStyleProcessorBuilder(messageOutput)

    args
      .asSequence()

      // Skip first argument "format" -- routing from the top level
      .drop(1)

      // try to treat every two arguments as an argument and its param.
      // If param is not needed we'll take it as an arg on the next iteration,
      // otherwise we'll skip using a boolean flag `skipNext`
      .windowed(size = 2, step = 1, partialWindows = true)

      .forEach { argAndParam ->
        val arg = argAndParam[0]
        val param = argAndParam.getOrNull(1)

        if (skipNext) {
          skipNext = false
          return@forEach
        }

        when (arg) {
          "-h", "-help" -> showUsageAndExit()
          "-r", "-R" -> builder.recursive()
          "-d", "-dry" -> builder.dryRun()
          "-s", "-settings" -> {
            param ?: throw fatalError("Missing settings file path.")
            builder.withCodeStyleSettings(readSettings(param) ?: throw fatalError("Cannot find file $param"))
            skipNext = true
          }
          "-m", "-mask" -> {
            builder.withFileMasks(param ?: throw fatalError("Missing file mask(s)."))
            skipNext = true
          }
          else -> {
            if (arg.startsWith("-")) throw fatalError("Unknown option $arg")
            builder.withEntry(arg)
          }
        }
      }

    return builder.build()
  }

}

private const val usageInfo = """
Usage: format [-h] [-r|-R] [-d|-dry] [-s|-settings settingsPath] path1 path2...
  -h|-help       Show a help message and exit.
  -s|-settings   A path to Intellij IDEA code style settings .xml file.
  -r|-R          Scan directories recursively.
  -d|-dry        Perform a dry run: no file modifications, only exit status
  -m|-mask       A comma-separated list of file masks.
  path<n>        A path to a file or a directory.  
"""

private fun readSettings(settingsPath: String): CodeStyleSettings? =
  VfsUtil.findFileByIoFile(File(settingsPath), true)
    ?.let { CodeStyleSettingsLoader().loadSettings(it) }


private val appInfo: String =
  (ApplicationInfoEx.getInstanceEx() as ApplicationInfoImpl)
    .let { "${it.fullApplicationName}, build ${it.build.asString()}" }
