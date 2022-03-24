// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.commandLine

import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ArgumentsException
import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ShowUsageException
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
import java.nio.charset.Charset
import kotlin.system.exitProcess

private val LOG = Logger.getInstance(FormatterStarter::class.java)

/**
 * A launcher class for command-line formatter.
 */
internal class FormatterStarter : ApplicationStarter {
  val messageOutput = StdIoMessageOutput

  override fun getCommandName() = "format"

  override fun main(args: Array<String>) {
    messageOutput.info("$appInfo Formatter\n")
    LOG.info(args.joinToString(",", prefix = "Attributes: "))

    val processor = try {
      createFormatter(args, messageOutput)
    }
    catch (e: ShowUsageException) {
      messageOutput.info(usageInfo)
      exitProcess(0)
    }
    catch (e: ArgumentsException) {
      messageOutput.error("ERROR: ${e.message}\n")
      exitProcess(1)
    }

    try {
      processor.use {
        it.processFiles()
        it.printReport()
        if (!it.isResultSuccessful()) {
          exitProcess(1)
        }
      }
    }
    catch (e: IOException) {
      messageOutput.error("ERROR: ${e.localizedMessage}\n")
      exitProcess(1)
    }

    (ApplicationManager.getApplication() as ApplicationEx).exit(true, true)
  }

}

fun createFormatter(args: Array<String>, messageOutput: MessageOutput = StdIoMessageOutput) =
  CodeStyleProcessorBuilder(messageOutput)
    .apply {
      if (args.size < 2) throw ShowUsageException()
      val skipFlag = Skipper()

      args
        .asSequence().drop(1)  // Skip first argument "format" -- routing from the top level

        // try to treat every two arguments as an argument and its param.
        // If param is not needed we'll take it as an arg on the next iteration,
        // otherwise we'll skip using a boolean flag `skipNext`
        .windowed(size = 2, step = 1, partialWindows = true) { Pair(it[0], it.getOrNull(1)) }
        .forEach { (arg, param) ->
          skipFlag.check { return@forEach }

          when (arg) {
            "-h", "-help" -> throw ShowUsageException()
            "-r", "-R" -> recursive()
            "-d", "-dry" -> dryRun()
            "-s", "-settings" -> {
              param ?: throw ArgumentsException("Missing settings file path.")
              withCodeStyleSettings(readSettings(param) ?: throw ArgumentsException("Cannot find file $param"))
              skipFlag.skip()
            }
            "-m", "-mask" -> {
              withFileMasks(param ?: throw ArgumentsException("Missing file mask(s)."))
              skipFlag.skip()
            }
            "-charset" -> {
              param ?: throw ArgumentsException("Missing file mask(s).")
              runCatching { Charset.forName(param) }
                .onSuccess { withCharset(it) }
                .onFailure { messageOutput.error("Ignoring charset setting: ${it.message}") }
              skipFlag.skip()
            }
            "-allowDefaults" -> allowFactoryDefaults()
            else -> {
              if (arg.startsWith("-")) throw ArgumentsException("Unknown option $arg")
              withEntry(arg)
            }
          }
        }
    }
    .build()


private const val usageInfo = """
Usage: format [-h] [-r|-R] [-d|-dry] [-s|-settings settingsPath] [-charset charsetName] [-allowDefaults] path1 path2...
  -h|-help         Show a help message and exit.
  -s|-settings     A path to Intellij IDEA code style settings .xml file. This setting will be
                   be used as a primary one regardless to the surrounding project settings
  -r|-R            Scan directories recursively.
  -d|-dry          Perform a dry run: no file modifications, only exit status.
  -m|-mask         A comma-separated list of file masks.
  -charset         Force charset to use when reading and writing files.
  -allowDefaults   Use factory defaults when style is not defined for a given file. I.e. when -s
                   is not not set and file doesn't belong to any IDEA project. Otherwise file will
                   be ignored.
  path<n>        A path to a file or a directory.  
"""

fun readSettings(settingsFile: File): CodeStyleSettings? =
  VfsUtil.findFileByIoFile(settingsFile, true)
    ?.let {
      it.refresh(false, false)
      CodeStyleSettingsLoader().loadSettings(it)
    }

private fun readSettings(settingsPath: String): CodeStyleSettings? =
  readSettings(File(settingsPath))


private val appInfo: String =
  (ApplicationInfoEx.getInstanceEx() as ApplicationInfoImpl)
    .let { "${it.fullApplicationName}, build ${it.build.asString()}" }

sealed class CodeStyleProcessorBuildException : RuntimeException {
  constructor() : super()
  constructor(message: String) : super(message)

  class ShowUsageException : CodeStyleProcessorBuildException()
  class ArgumentsException(message: String) : CodeStyleProcessorBuildException(message)
}

private class Skipper(private var skip: Boolean = false) {
  fun skip() {
    skip = true
  }

  inline fun check(action: () -> Unit) {
    if (skip) {
      skip = false
      action()
    }
  }
}
