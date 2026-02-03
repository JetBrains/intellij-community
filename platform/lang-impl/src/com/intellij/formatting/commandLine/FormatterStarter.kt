// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.commandLine

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ArgumentsException
import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ShowUsageException
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.codeStyle.CodeStyleSettingsLoader
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*

private val LOG = Logger.getInstance(FormatterStarter::class.java)

/**
 * A launcher class for command-line formatter.
 */
internal class FormatterStarter : ApplicationStarter {
  private val messageOutput = StdIoMessageOutput

  override fun main(args: List<String>) {
    messageOutput.info("$appInfo Formatter\n")
    LOG.info(args.joinToString(",", prefix = "Attributes: "))

    val builder = try {
      createBuilder(args, messageOutput)
    }
    catch (e: ShowUsageException) {
      messageOutput.info(usageInfo)
      exit(0)
      return
    }
    catch (e: ArgumentsException) {
      messageOutput.error("ERROR: ${e.message}\n")
      exit(1)
      return
    }

    val projectUID = UUID.randomUUID().toString()
    val project = createProject(projectUID)
    val processor = builder.build(project)

    try {
      processor.let {
        it.processFiles()
        it.printReport()
        if (!it.isResultSuccessful()) {
          exit(1)
          return
        }
      }
    }
    catch (e: IOException) {
      messageOutput.error("ERROR: ${e.localizedMessage}\n")
      exit(1)
      //return
    }
    finally {
      ProjectManager.getInstance().closeAndDispose(project)
    }

    exit(0)
  }
}



fun createBuilder(args: List<String>, messageOutput: MessageOutput = StdIoMessageOutput): CodeStyleProcessorBuilder =
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

fun readSettings(settingsFile: File): CodeStyleSettings? {
  return VfsUtil.findFileByIoFile(settingsFile, true)
    ?.let {
      it.refresh(false, false)
      CodeStyleSettingsLoader().loadSettings(it)
    }
}

private fun readSettings(settingsPath: String): CodeStyleSettings? = readSettings(File(settingsPath))

private val appInfo: String
  get() = ApplicationInfo.getInstance()
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

private fun exit(code: Int) {
  application.exit(true, true, false, code)
}

private val PROJECT_DIR_PREFIX = PlatformUtils.getPlatformPrefix() + ".format."
private const val PROJECT_DIR_SUFFIX = ".tmp"

private fun createProjectDir(projectUID: String) = FileUtil
  .createTempDirectory(PROJECT_DIR_PREFIX, projectUID + PROJECT_DIR_SUFFIX)
  .toPath()
  .resolve(PathMacroUtil.DIRECTORY_STORE_NAME)
  .also { Files.createDirectories(it) }

private fun createProject(projectUID: String) =
  ProjectManagerEx.getInstanceEx()
    .openProject(createProjectDir(projectUID), OpenProjectTask(isNewProject = true))
    ?.also {
      CodeStyle.setMainProjectSettings(it, CodeStyleSettingsManager.getInstance().createSettings())
    }
  ?: throw RuntimeException("Failed to create temporary project $projectUID")