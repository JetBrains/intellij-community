// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
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

  private fun createFormatter(args: Array<String>): FileSetCodeStyleProcessor {
    if (args.size < 2) {
      showUsageAndExit()
    }

    var skipNext = false

    var isRecursive = false
    var isDryRun = false
    var codeStyleSettings = CodeStyleSettingsManager.getInstance().createSettings()
    val masks = arrayListOf<String>()
    val entries = arrayListOf<String>()

    args
      .asSequence()
      .windowed(size = 2, step = 1, partialWindows = true)
      .forEach { pair ->

        if (skipNext) {
          skipNext = false
          return@forEach
        }

        val arg = pair[0]
        val argParam = pair.getOrNull(1)

        when (arg) {
          "-h", "-help" -> showUsageAndExit()
          "-r", "-R" -> isRecursive = true
          "-d", "-dry" -> isDryRun = true
          "-s", "-settings" -> {
            argParam ?: throw fatalError("Missing settings file path.")
            val settings = readSettings(argParam) ?: throw fatalError("Cannot find file $argParam")
            codeStyleSettings = settings
            skipNext = true
          }
          "-m", "-mask" -> {
            argParam ?: throw fatalError("Missing file mask(s).")
            argParam.split(',')
              .filter { it.isNotBlank() }
              .forEach { masks.add(it) }
            skipNext = true
          }
          else -> {
            if (arg.startsWith("-")) throw fatalError("Unknown option $arg")
            entries.add(arg)
          }
        }
      }

    try {
      val createProcessor = if (isDryRun) ::FileSetFormatValidator else ::FileSetFormatter

      return createProcessor(codeStyleSettings, messageOutput, isRecursive).apply {
        masks.forEach(this::addFileMask)
        entries.forEach(this::addEntry)
      }
    }
    catch (e: IOException) {
      throw fatalError(e.localizedMessage)
    }
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

private fun readSettings(settingsPath: String) =
  VfsUtil.findFileByIoFile(File(settingsPath), true)
    ?.let { CodeStyleSettingsLoader().loadSettings(it) }

private val appInfo =
  (ApplicationInfoEx.getInstanceEx() as ApplicationInfoImpl)
    .apply { "$fullApplicationName, build ${build.asString()}" }
