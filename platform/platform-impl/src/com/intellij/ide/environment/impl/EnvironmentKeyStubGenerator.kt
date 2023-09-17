// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.blockingContext
import com.intellij.platform.util.ArgsParser
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createFile
import kotlin.system.exitProcess

class EnvironmentKeyStubGenerator : ModernApplicationStarter() {

  override val commandName: String = COMMAND_NAME

  override suspend fun start(args: List<String>) {
    performGeneration(args)

    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().exit(false, true, false)
    }
  }

  suspend fun performGeneration(args: List<String>, configuration: EnvironmentConfiguration = EnvironmentConfiguration.EMPTY) {
    val parsedArgs = parseCommandLine(args)

    val config = generateKeyConfig(!parsedArgs.noDescriptions, configuration)

    withContext(Dispatchers.IO) {
      try {
        if (parsedArgs.stdout) {
          println(config.toString(Charsets.UTF_8))
        } else {
          val path = parsedArgs.outputFileName ?: Path(DEFAULT_FILE_NAME)
          path.createParentDirectories().createFile().write(config)
          thisLogger().info("Configuration keys are successfully written to ${path.absolute()}")
        }
      }
      catch (e: FileAlreadyExistsException) {
        thisLogger().error("File already exists. No operations were performed.")
      }
    }
  }

  companion object {
    const val COMMAND_NAME: String = "generateEnvironmentKeysFile"
  }
}

private suspend fun generateKeyConfig(generateDescriptions: Boolean, configuration: EnvironmentConfiguration): ByteArray {
  val environmentKeys = blockingContext {
    EnvironmentKeyProvider.EP_NAME.extensionList.flatMap { it.knownKeys.toList() }
  }.sortedBy { it.first.id }

  val registeredKeys = environmentKeys.mapTo(HashSet()) { it.first }
  val unregisteredValues = configuration.map.entries.filter { it.key !in registeredKeys }

  val byteStream = ByteArrayOutputStream()
  val generator = JsonFactory().createGenerator(byteStream).setPrettyPrinter(KeyConfigPrettyPrinter())
  with(generator) {
    writeStartArray()
    for ((key, descr) in environmentKeys) {
      writeStartObject()
      if (generateDescriptions) {
        writeArrayFieldStart("description")
        for (line in descr.get().lines()) {
          writeString(line)
        }
        writeEndArray()
      }
      writeStringField("key", key.id)
      writeStringField("value", configuration.get(key) ?: "")
      writeEndObject()
    }
    for ((key, value) in unregisteredValues) {
      writeStartObject()
      writeStringField("key", key.id)
      writeStringField("value", value)
      writeEndObject()
    }
    writeEndArray()
    close()
  }
  return byteStream.toByteArray()
}

private class KeyConfigPrettyPrinter : DefaultPrettyPrinter() {
  private val INDENTER = DefaultIndenter("  ", "\n")
  override fun createInstance(): DefaultPrettyPrinter = KeyConfigPrettyPrinter()

  init {
    _objectFieldValueSeparatorWithSpaces = ": "
    _objectIndenter = INDENTER
    _arrayIndenter = INDENTER
  }
}

private class GenerateEnvironmentKeysArgs(parser: ArgsParser) {

  val outputFileName: Path? by parser.arg(FILE_ARGUMENT_NAME,
                                          """Path to file with configuration keys. Default: '$DEFAULT_FILE_NAME'.
                                            |Cannot be specified together with --$STDOUT_ARGUMENT_NAME.
                                            |""".trimMargin()).fileOrNull()
  val stdout: Boolean by parser.arg(STDOUT_ARGUMENT_NAME,
                                    """Prints the content of configuration file to stdout instead of file.
                                        |Cannot be specified together with --$FILE_ARGUMENT_NAME.
                                        |""".trimMargin()).flag()

  val noDescriptions: Boolean by parser.arg(NO_DESCRIPTIONS_ARGUMENT_NAME, "Disables generation of description for each key").flag()
}


private fun parseCommandLine(args: List<String>) : GenerateEnvironmentKeysArgs {
  return try {
    val parser = ArgsParser(args)
    val commandArgs = GenerateEnvironmentKeysArgs(parser)
    parser.tryReadAll()
    if (commandArgs.stdout && commandArgs.outputFileName != null) {
      throw IllegalStateException("Only one of --$STDOUT_ARGUMENT_NAME and --$FILE_ARGUMENT_NAME can be specified")
    }
    commandArgs
  }
  catch (t: Throwable) {
    val argsParser = ArgsParser(listOf())
    runCatching { GenerateEnvironmentKeysArgs(argsParser) }
    logger<EnvironmentKeyStubGenerator>().error(
      """Failed to parse commandline: ${t.message}
  |Usage:
  |
  |options:
    ${argsParser.usage(includeHidden = true)}""".trimMargin())
    exitProcess(2)
  }
}


private const val DEFAULT_FILE_NAME : String = "environmentKeys.json"
private const val FILE_ARGUMENT_NAME : String = "file"
private const val STDOUT_ARGUMENT_NAME : String = "stdout"
private const val NO_DESCRIPTIONS_ARGUMENT_NAME : String = "no-descriptions"
