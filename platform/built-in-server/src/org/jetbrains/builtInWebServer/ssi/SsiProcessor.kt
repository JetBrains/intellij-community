/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer.ssi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SmartList
import com.intellij.util.text.CharArrayUtil
import gnu.trove.THashMap
import io.netty.buffer.ByteBufUtf8Writer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

internal val LOG = Logger.getInstance(SsiProcessor::class.java)

internal val COMMAND_START = "<!--#"
internal val COMMAND_END = "-->"

class SsiStopProcessingException : RuntimeException()

class SsiProcessor(allowExec: Boolean) {
  private val commands: MutableMap<String, SsiCommand> = THashMap()

  init {
    commands.put("config", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        for (i in paramNames.indices) {
          val paramName = paramNames[i]
          val paramValue = paramValues[i]
          val substitutedValue = state.substituteVariables(paramValue)
          if (paramName.equals("errmsg", ignoreCase = true)) {
            state.configErrorMessage = substitutedValue
          }
          else if (paramName.equals("sizefmt", ignoreCase = true)) {
            state.configSizeFmt = substitutedValue
          }
          else if (paramName.equals("timefmt", ignoreCase = true)) {
            state.setConfigTimeFormat(substitutedValue, false)
          }
          else {
            LOG.info("#config--Invalid attribute: " + paramName)
            // We need to fetch this value each time, since it may change during the loop
            writer.write(state.configErrorMessage)
          }
        }
        return 0
      }
    })
    commands.put("echo", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        var encoding = "entity"
        var originalValue: String? = null
        val errorMessage = state.configErrorMessage
        for (i in paramNames.indices) {
          val paramName = paramNames[i]
          val paramValue = paramValues[i]
          if (paramName.equals("var", ignoreCase = true)) {
            originalValue = paramValue
          }
          else if (paramName.equals("encoding", ignoreCase = true)) {
            if (paramValue.equals("url", ignoreCase = true) || paramValue.equals("entity", ignoreCase = true) || paramValue.equals("none", ignoreCase = true)) {
              encoding = paramValue
            }
            else {
              LOG.info("#echo--Invalid encoding: " + paramValue)
              writer.write(errorMessage)
            }
          }
          else {
            LOG.info("#echo--Invalid attribute: " + paramName)
            writer.write(errorMessage)
          }
        }
        val variableValue = state.getVariableValue(originalValue!!, encoding)
        writer.write(variableValue ?: "(none)")
        return System.currentTimeMillis()
      }
    })
    //noinspection StatementWithEmptyBody
    if (allowExec) {
      // commands.put("exec", new SsiExec());
    }
    commands.put("include", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        var lastModified: Long = 0
        val configErrorMessage = state.configErrorMessage
        for (i in paramNames.indices) {
          val paramName = paramNames[i]
          if (paramName.equals("file", ignoreCase = true) || paramName.equals("virtual", ignoreCase = true)) {
            val substitutedValue = state.substituteVariables(paramValues[i])
            try {
              val virtual = paramName.equals("virtual", ignoreCase = true)
              lastModified = state.ssiExternalResolver.getFileLastModified(substitutedValue, virtual)
              val file = state.ssiExternalResolver.findFile(substitutedValue, virtual)
              if (file == null) {
                LOG.warn("#include-- Couldn't find file: " + substitutedValue)
                return 0
              }

              val `in` = FileInputStream(file)
              try {
                writer.write(`in`, file.length().toInt())
              }
              finally {
                `in`.close()
              }
            }
            catch (e: IOException) {
              LOG.warn("#include--Couldn't include file: " + substitutedValue, e)
              writer.write(configErrorMessage)
            }

          }
          else {
            LOG.info("#include--Invalid attribute: " + paramName)
            writer.write(configErrorMessage)
          }
        }
        return lastModified
      }
    })
    commands.put("flastmod", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        var lastModified: Long = 0
        val configErrMsg = state.configErrorMessage
        for (i in paramNames.indices) {
          val paramName = paramNames[i]
          val paramValue = paramValues[i]
          val substitutedValue = state.substituteVariables(paramValue)
          if (paramName.equals("file", ignoreCase = true) || paramName.equals("virtual", ignoreCase = true)) {
            val virtual = paramName.equals("virtual", ignoreCase = true)
            lastModified = state.ssiExternalResolver.getFileLastModified(substitutedValue, virtual)
            val strftime = Strftime(state.configTimeFmt, Locale.US)
            writer.write(strftime.format(Date(lastModified)))
          }
          else {
            LOG.info("#flastmod--Invalid attribute: " + paramName)
            writer.write(configErrMsg)
          }
        }
        return lastModified
      }
    })
    commands.put("fsize", SsiFsize())
    commands.put("printenv", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        var lastModified: Long = 0
        // any arguments should produce an error
        if (paramNames.isEmpty()) {
          val variableNames = LinkedHashSet<String>()
          //These built-in variables are supplied by the mediator ( if not over-written by the user ) and always exist
          variableNames.add("DATE_GMT")
          variableNames.add("DATE_LOCAL")
          variableNames.add("LAST_MODIFIED")
          state.ssiExternalResolver.addVariableNames(variableNames)
          for (variableName in variableNames) {
            var variableValue: String? = state.getVariableValue(variableName)
            // This shouldn't happen, since all the variable names must have values
            if (variableValue == null) {
              variableValue = "(none)"
            }
            writer.append(variableName).append('=').append(variableValue).append('\n')
            lastModified = System.currentTimeMillis()
          }
        }
        else {
          writer.write(state.configErrorMessage)
        }
        return lastModified
      }
    })
    commands.put("set", object : SsiCommand {
      override fun process(state: SsiProcessingState, commandName: String, paramNames: List<String>, paramValues: Array<String>, writer: ByteBufUtf8Writer): Long {
        var lastModified: Long = 0
        val errorMessage = state.configErrorMessage
        var variableName: String? = null
        for (i in paramNames.indices) {
          val paramName = paramNames[i]
          val paramValue = paramValues[i]
          if (paramName.equals("var", ignoreCase = true)) {
            variableName = paramValue
          }
          else if (paramName.equals("value", ignoreCase = true)) {
            if (variableName != null) {
              val substitutedValue = state.substituteVariables(paramValue)
              state.ssiExternalResolver.setVariableValue(variableName, substitutedValue)
              lastModified = System.currentTimeMillis()
            }
            else {
              LOG.info("#set--no variable specified")
              writer.write(errorMessage)
              throw SsiStopProcessingException()
            }
          }
          else {
            LOG.info("#set--Invalid attribute: " + paramName)
            writer.write(errorMessage)
            throw SsiStopProcessingException()
          }
        }
        return lastModified
      }
    })

    val ssiConditional = SsiConditional()
    commands.put("if", ssiConditional)
    commands.put("elif", ssiConditional)
    commands.put("endif", ssiConditional)
    commands.put("else", ssiConditional)
  }

  /**
   * @return the most current modified date resulting from any SSI commands
   */
  fun process(ssiExternalResolver: SsiExternalResolver, file: File, writer: ByteBufUtf8Writer): Long {
    val fileContents = FileUtilRt.loadFileText(file)
    var lastModifiedDate = file.lastModified()
    val ssiProcessingState = SsiProcessingState(ssiExternalResolver, lastModifiedDate)
    var index = 0
    var inside = false
    val command = StringBuilder()
    writer.ensureWritable(file.length().toInt())
    try {
      while (index < fileContents.size) {
        val c = fileContents[index]
        if (inside) {
          if (c == COMMAND_END[0] && charCmp(fileContents, index, COMMAND_END)) {
            inside = false
            index += COMMAND_END.length
            val commandName = parseCommand(command)
            if (LOG.isDebugEnabled) {
              LOG.debug("SSIProcessor.process -- processing command: " + commandName)
            }
            val paramNames = parseParamNames(command, commandName.length)
            val paramValues = parseParamValues(command, commandName.length, paramNames.size)
            // We need to fetch this value each time, since it may change during the loop
            val configErrMsg = ssiProcessingState.configErrorMessage
            val ssiCommand = commands[commandName.toLowerCase(Locale.ENGLISH)]
            var errorMessage: String? = null
            if (ssiCommand == null) {
              errorMessage = "Unknown command: $commandName"
            }
            else if (paramValues == null) {
              errorMessage = "Error parsing directive parameters."
            }
            else if (paramNames.size != paramValues.size) {
              errorMessage = "Parameter names count does not match parameter values count on command: $commandName"
            }
            else {
              // don't process the command if we are processing conditional commands only and the command is not conditional
              if (!ssiProcessingState.conditionalState.processConditionalCommandsOnly || ssiCommand is SsiConditional) {
                val newLastModified = ssiCommand.process(ssiProcessingState, commandName, paramNames, paramValues, writer)
                if (newLastModified > lastModifiedDate) {
                  lastModifiedDate = newLastModified
                }
              }
            }
            if (errorMessage != null) {
              LOG.warn(errorMessage)
              writer.write(configErrMsg)
            }
          }
          else {
            command.append(c)
            index++
          }
        }
        else if (c == COMMAND_START[0] && charCmp(fileContents, index, COMMAND_START)) {
          inside = true
          index += COMMAND_START.length
          command.setLength(0)
        }
        else {
          if (!ssiProcessingState.conditionalState.processConditionalCommandsOnly) {
            writer.append(c)
          }
          index++
        }
      }
    }
    catch (e: SsiStopProcessingException) {
      //If we are here, then we have already stopped processing, so all is good
    }

    return lastModifiedDate
  }

  protected fun parseParamNames(command: StringBuilder, start: Int): List<String> {
    var bIdx = start
    val values = SmartList<String>()
    var inside = false
    val builder = StringBuilder()
    while (bIdx < command.length) {
      if (inside) {
        while (bIdx < command.length && command[bIdx] != '=') {
          builder.append(command[bIdx])
          bIdx++
        }

        values.add(builder.toString())
        builder.setLength(0)
        inside = false
        var quotes = 0
        var escaped = false
        while (bIdx < command.length && quotes != 2) {
          val c = command[bIdx]
          // Need to skip escaped characters
          if (c == '\\' && !escaped) {
            escaped = true
            bIdx++
            continue
          }
          if (c == '"' && !escaped) {
            quotes++
          }
          escaped = false
          bIdx++
        }
      }
      else {
        while (bIdx < command.length && isSpace(command[bIdx])) {
          bIdx++
        }
        if (bIdx >= command.length) {
          break
        }
        inside = true
      }
    }
    return values
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  protected fun parseParamValues(command: StringBuilder, start: Int, count: Int): Array<String>? {
    var valueIndex = 0
    var inside = false
    val values = arrayOfNulls<String>(count)
    val builder = StringBuilder()
    var endQuote: Char = 0.toChar()
    var bIdx = start
    while (bIdx < command.length) {
      if (!inside) {
        while (bIdx < command.length && !isQuote(command[bIdx])) {
          bIdx++
        }
        if (bIdx >= command.length) {
          break
        }
        inside = true
        endQuote = command[bIdx]
      }
      else {
        var escaped = false
        while (bIdx < command.length) {
          val c = command[bIdx]
          // Check for escapes
          if (c == '\\' && !escaped) {
            escaped = true
            bIdx++
            continue
          }
          // If we reach the other " then stop
          if (c == endQuote && !escaped) {
            break
          }
          // Since parsing of attributes and var
          // substitution is done in separate places,
          // we need to leave escape in the string
          if (c == '$' && escaped) {
            builder.append('\\')
          }
          escaped = false
          builder.append(c)
          bIdx++
        }
        // If we hit the end without seeing a quote
        // the signal an error
        if (bIdx == command.length) {
          return null
        }
        values[valueIndex++] = builder.toString()
        builder.setLength(0)
        inside = false
      }
      bIdx++
    }
    @Suppress("CAST_NEVER_SUCCEEDS")
    return values as Array<String>
  }

  private fun parseCommand(instruction: StringBuilder): String {
    var firstLetter = -1
    var lastLetter = -1
    for (i in 0..instruction.length - 1) {
      val c = instruction[i]
      if (Character.isLetter(c)) {
        if (firstLetter == -1) {
          firstLetter = i
        }
        lastLetter = i
      }
      else if (isSpace(c)) {
        if (lastLetter > -1) {
          break
        }
      }
      else {
        break
      }
    }
    return if (firstLetter == -1) "" else instruction.substring(firstLetter, lastLetter + 1)
  }

  protected fun charCmp(buf: CharArray, index: Int, command: String) = CharArrayUtil.regionMatches(buf, index, index + command.length, command)

  protected fun isSpace(c: Char) = c == ' ' || c == '\n' || c == '\t' || c == '\r'

  protected fun isQuote(c: Char) = c == '\'' || c == '\"' || c == '`'
}