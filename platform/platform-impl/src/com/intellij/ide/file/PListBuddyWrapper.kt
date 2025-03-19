// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.file

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.util.createDocumentBuilder
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader

@Deprecated("Use dd-plist library to work with plist files (https://github.com/3breadt/dd-plist)")
@ApiStatus.Internal
class PListBuddyWrapper(private val pListPath: String) {
  private val UTIL_PATH: String = "/usr/libexec/PListBuddy"
  private val MAX_CHARS_OUTPUT = 20
  
  enum class OutputType {
    DEFAULT, XML
  }
  
  @Throws(PListProcessingException::class)
  fun runCommand(command: String): CommandResult {
    return runCommand(OutputType.DEFAULT, command)
  }

  @Throws(PListProcessingException::class)
  fun runCommand(outputType: OutputType, vararg commands: String): CommandResult {
    val commandLine = GeneralCommandLine().withExePath(UTIL_PATH)
    for (command in commands) {
      if (OutputType.XML == outputType) {
        commandLine.addParameter("-x")
      }
      commandLine.addParameter("-c")
      commandLine.addParameter(command)
    }
    commandLine.addParameter(pListPath)
    val errorMessage = StringBuilder()
    val output = StringBuilder()
    try {
      val processHandler = OSProcessHandler(commandLine)
      processHandler.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          if (ProcessOutputTypes.STDOUT == outputType) {
            output.append(event.text)
          }
          if (ProcessOutputTypes.STDERR == outputType) {
            errorMessage.append(event.text)
          }
        }
      })
      processHandler.startNotify()
      if (!processHandler.waitFor(1000)) {
        throw PListProcessingException("Failed to execute command on file $pListPath in 1 sec")
      }
      if (!errorMessage.isEmpty()) {
        throw PListProcessingException("PListBuddy execution process for file $pListPath returned error: $errorMessage")
      }
      return CommandResult((processHandler.exitCode ?: -1), output.toString())
    }
    catch (exe: ExecutionException) {
      throw PListProcessingException("Failed to run PListBuddy and execute command on file $pListPath: " + exe.localizedMessage)
    }
  }

  private fun truncateOutput(output: String): String {
    return if (output.length < MAX_CHARS_OUTPUT) output else output.substring(0, MAX_CHARS_OUTPUT) + "..."
  }

  @Throws(PListProcessingException::class)
  fun readData(dataId: String): Document {
    val command = "Print $dataId"
    val result = runCommand(OutputType.XML, command)
    if (result.retCode != 0) {
      throw PListProcessingException("PListBuddy return code for command $command is: " + result.retCode)
    }
    val output = result.output
    if (output != null && !output.startsWith("<?xml")) {
      throw PListProcessingException("Unexpected output for command $command: " + truncateOutput(output))
    }
    if (output == null) {
      throw PListProcessingException("Empty output for command $command")
    }
    return parseXml(output)
  }

  @Throws(PListProcessingException::class)
  private fun parseXml(xmlString: String): Document {
    try {
      val builder = createDocumentBuilder()
      val stringReader = StringReader(xmlString)
      val source = InputSource(stringReader)
      return builder.parse(source)
    }
    catch (e: SAXException) {
      throw PListProcessingException("XML read error: " + e.message)
    }
    catch (e: IOException) {
      throw PListProcessingException("XML read error: " + e.message)
    }
  }

  class CommandResult (val retCode: Int, val output: String?)

}
 
@ApiStatus.Internal
class PListProcessingException(message: String?) : Exception(message)