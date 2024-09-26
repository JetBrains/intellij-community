// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellChildOptionsContext

class JavaShellCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return listOf(ShellCommandSpecInfo.create(getSpecs(), ShellCommandSpecConflictStrategy.REPLACE))
  }

  private fun getSpecs(): ShellCommandSpec = ShellCommandSpec("java") {
    parserOptions = ShellCommandParserOptions.create(flagsArePosixNonCompliant = true, optionsMustPrecedeArguments = true)

    dynamicOptions { terminalContext ->
      val javaContext = JavaShellCommandContext.create(terminalContext)
      addOptionsFromVM(javaContext?.getJrePath())
      val version = javaContext?.getJavaVersion() ?: return@dynamicOptions
      if (version.isAtLeast(11)) {
        addOptionsFromJava11()
      } else if (version.isAtLeast(8)) {
        addOptionsFromJava8()
      }
    }

    val errorStreamName: @Nls String = JavaTerminalBundle.message("error.stream.name")
    description(JavaTerminalBundle.message("java.command.terminal.description"))
    option("-?", "-help", "-h") {
      exclusiveOn = listOf("--help")
      description(JavaTerminalBundle.message("java.command.terminal.help.option.description", errorStreamName))
    }
    option("-jar") {
      argument {
        displayName(JavaTerminalBundle.message("java.command.terminal.jar.option.argument.jar.file.text"))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator())
      }
      description(JavaTerminalBundle.message("java.command.terminal.jar.option.description"))
    }
    option("-version") {
      exclusiveOn = listOf("--version")
      description(JavaTerminalBundle.message("java.command.terminal.version.option.description", errorStreamName))
    }
    option("-classpath", "-cp") {
      exclusiveOn = listOf("--class-path")
      description(JavaTerminalBundle.message("java.command.terminal.classpath.option.description"))
      argument {
        displayName(JavaTerminalBundle.message("java.command.terminal.classpath.option.argument.path.text", ShellCommandUtils.getClassPathSeparator()))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator())
      }
    }
    option("-showversion") {
      exclusiveOn = listOf("--show-version")
      description(JavaTerminalBundle.message("java.command.terminal.show.version.option.description", errorStreamName))
    }
    argument {
      displayName(JavaTerminalBundle.message("java.command.terminal.argument.main.class.text"))
      suggestions(ShellDataGenerators.fileSuggestionsGenerator())
    }
  }

  private suspend fun ShellChildOptionsContext.addOptionsFromVM(path: String?) {
    val optionsService = VMOptionsService.getInstance()
    val jdkOptionsData = withContext(Dispatchers.IO) {
      if (path == null) return@withContext null
      optionsService.getOrComputeOptionsForJdk(path).get()
    } ?: optionsService.getStandardOptions()
    jdkOptionsData.options
      .filter { (it.kind == VMOptionKind.Standard || it.kind == VMOptionKind.Product) && (it.variant != VMOptionVariant.XX)}
      .toList()
      .forEach {
      val optionName = it.optionName
      val presentableName = "${it.variant.prefix()}$optionName"
      option(presentableName) {
        if (!JavaTerminalBundle.isMessageInBundle(getOptionBundleKey(optionName))) {
          LOG.warn("Unknown ${it.variant} option: \"$optionName\". Provide ${getOptionBundleKey(optionName)} and [${getOptionArgumentBundleKey(optionName)}]")
          return@option
        }
        description(JavaTerminalBundle.message(getOptionBundleKey(optionName)))
        if (!JavaTerminalBundle.isMessageInBundle(getOptionArgumentBundleKey(optionName))) return@option

        val info = OPTION_UI_INFO_MAP[presentableName] ?: DEFAULT_UI_OPTION_INSTANCE
        repeatTimes = info.repeatTimes
        separator = info.separator
        argument {
          isOptional = info.isArgumentOptional
          displayName(JavaTerminalBundle.message(getOptionArgumentBundleKey(optionName)))
        }
      }
    }
  }

  private fun ShellChildOptionsContext.addOptionsFromJava11() {
    val outputStreamName = JavaTerminalBundle.message("output.stream.name")
    option("--version") {
      exclusiveOn = listOf("-version")
      description(JavaTerminalBundle.message("java.command.terminal.version.option.description", outputStreamName))
    }
    option("--show-version") {
      exclusiveOn = listOf("-show-version")
      description(JavaTerminalBundle.message("java.command.terminal.show.version.option.description", outputStreamName))
    }
    option("--dry-run") {
      exclusiveOn = listOf("-dry-run")
      description(JavaTerminalBundle.message("java.command.terminal.dry.run.option.description"))
    }
    option("--class-path") {
      description(JavaTerminalBundle.message("java.command.terminal.classpath.option.description"))
      argument {
        displayName(JavaTerminalBundle.message("java.command.terminal.classpath.option.argument.path.text", ShellCommandUtils.getClassPathSeparator()))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator())
      }
    }
    option("--help") {
      exclusiveOn = listOf("-?", "-help", "-h")
      description(JavaTerminalBundle.message("java.command.terminal.help.option.description", outputStreamName))
    }
    option("--enable-preview") {
      description(JavaTerminalBundle.message("java.command.terminal.enable.preview.option.description"))
    }

    option("-verbose") {
      description(JavaTerminalBundle.message("java.command.terminal.verbose.option.description"))
      separator=":"
      repeatTimes = 0
      argument {
        isOptional = true
        displayName(JavaTerminalBundle.message("java.command.terminal.verbose.option.argument.text.11"))
      }
    }
  }

  private fun ShellChildOptionsContext.addOptionsFromJava8() {
    option("-verbose") {
      description(JavaTerminalBundle.message("java.command.terminal.verbose.option.description"))
      separator=":"
      repeatTimes = 0
      argument {
        isOptional = true
        displayName(JavaTerminalBundle.message("java.command.terminal.verbose.option.argument.text.8"))
      }
    }
  }

  private fun getOptionBundleKey(option: String): String = "java.command.terminal.${getCanonicalOptionName(option)}.option.description"

  private fun getOptionArgumentBundleKey(option: String): String = "java.command.terminal.${getCanonicalOptionName(option)}.option.argument.text"

  private fun getCanonicalOptionName(option: String): String = option.replace(Regex("[:|\\-]"), ".").trim('=', '.')
}


private val OPTION_UI_INFO_MAP = mapOf(
  "-Xms" to UIOptionInfo(separator = ""),
  "-Xmx" to UIOptionInfo(separator = ""),
  "-Xmn" to UIOptionInfo(separator = ""),
  "-Xss" to UIOptionInfo(separator = ""),
  "-Xbootclasspath:" to UIOptionInfo(separator = ""),
  "-Xbootclasspath/a:" to UIOptionInfo(separator = ""),
  "-Xbootclasspath/p:" to UIOptionInfo(separator = ""),
  "-Xlog:" to UIOptionInfo(separator = ""),
  "-Xloggc:" to UIOptionInfo(separator = ""),
  "--add-opens" to UIOptionInfo(repeatTimes = 0),
  "--patch-module" to UIOptionInfo(repeatTimes = 0),
  "--limit-modules" to UIOptionInfo(repeatTimes = 0),
  "--add-reads" to UIOptionInfo(repeatTimes = 0),
  "--add-exports" to UIOptionInfo(repeatTimes = 0),
  "--finalization=" to UIOptionInfo(separator = ""),
  "--illegal-access=" to UIOptionInfo(separator = ""),
  "-ea" to UIOptionInfo(separator = ":", isArgumentOptional = true),
  "-da" to UIOptionInfo(separator = ":", isArgumentOptional = true),
  "-enableassertions" to UIOptionInfo(separator = ":", isArgumentOptional = true),
  "-disableassertions" to UIOptionInfo(separator = ":", isArgumentOptional = true),
  "-agentlib:" to UIOptionInfo(separator = ""),
  "-agentpath:" to UIOptionInfo(separator = ""),
  "-javaagent:" to UIOptionInfo(separator = ""),
  "-D" to UIOptionInfo(separator = "", repeatTimes = 0),
  "-XX:" to UIOptionInfo(repeatTimes = 0)
)

private data class UIOptionInfo(val separator: String? = null, val repeatTimes: Int = 1, val isArgumentOptional: Boolean = false)
private val DEFAULT_UI_OPTION_INSTANCE = UIOptionInfo()

private val LOG = Logger.getInstance(JavaShellCommandSpecsProvider::class.java)