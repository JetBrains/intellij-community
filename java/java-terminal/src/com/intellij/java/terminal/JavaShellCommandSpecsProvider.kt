// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.openapi.util.NlsSafe
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
        displayName(JAR_FILE_ARGUMENT_NAME)
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
        displayName(CLASSPATH_ARGUMENT_NAME)
      }
    }
    option("-showversion") {
      exclusiveOn = listOf("--show-version")
      description(JavaTerminalBundle.message("java.command.terminal.show.version.option.description", errorStreamName))
    }
    argument {
      displayName(MAIN_CLASS_ARGUMENT_NAME)
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
        val optionDescription = it.doc
        if (optionDescription == null) return@option
        description(optionDescription)

        val info = OPTION_UI_INFO_MAP[presentableName] ?: DEFAULT_UI_OPTION_INSTANCE
        repeatTimes = info.repeatTimes
        separator = info.separator
        val argumentName = info.argumentName
        if (argumentName != null) {
          argument {
            isOptional = info.isArgumentOptional
            displayName(argumentName)
          }
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
        displayName(CLASSPATH_ARGUMENT_NAME)
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
        displayName(CLASS_GC_GNI_MODULE_ARGUMENT_NAME)
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
        displayName(CLASS_GC_GNI_ARGUMENT_NAME)
      }
    }
  }
}


private const val SIZE_ARGUMENT_NAME = "size"
private const val PATH_ARGUMENT_NAME = "path"
private const val PACKAGE_AND_CLASS_ARGUMENT_NAME = "<package>|<class>"
private const val VALUE_ARGUMENT_NAME = "value"
private const val MODULE_PACKAGE_TARGET_MODULE_ARGUMENT_NAME = "<module>/<package>=<target-module>(,<target-module>)*"
private const val MAIN_CLASS_ARGUMENT_NAME: @NlsSafe String = "mainclass"
private const val JAR_FILE_ARGUMENT_NAME: @NlsSafe String = "jar file"
private const val CLASS_GC_GNI_ARGUMENT_NAME: @NlsSafe String = "class|gc|gni"
private const val CLASS_GC_GNI_MODULE_ARGUMENT_NAME: @NlsSafe String = "$CLASS_GC_GNI_ARGUMENT_NAME|<module>"
private val CLASSPATH_ARGUMENT_NAME: @NlsSafe String = "filepath[${JavaShellCommandUtils.getClassPathSeparator()}filepath]"

private val OPTION_UI_INFO_MAP = mapOf(
  "-Xms" to UIOptionInfo(separator = "", argumentName = SIZE_ARGUMENT_NAME),
  "-Xmx" to UIOptionInfo(separator = "", argumentName = SIZE_ARGUMENT_NAME),
  "-Xmn" to UIOptionInfo(separator = "", argumentName = SIZE_ARGUMENT_NAME),
  "-Xss" to UIOptionInfo(separator = "", argumentName = SIZE_ARGUMENT_NAME),
  "-Xbootclasspath:" to UIOptionInfo(separator = "", argumentName = PATH_ARGUMENT_NAME),
  "-Xbootclasspath/a:" to UIOptionInfo(separator = "", argumentName = PATH_ARGUMENT_NAME),
  "-Xbootclasspath/p:" to UIOptionInfo(separator = "", argumentName = PATH_ARGUMENT_NAME),
  "-Xlog:" to UIOptionInfo(separator = "", argumentName = "opts"),
  "-Xloggc:" to UIOptionInfo(separator = "", argumentName = "file"),
  "--add-opens" to UIOptionInfo(repeatTimes = 0, argumentName = MODULE_PACKAGE_TARGET_MODULE_ARGUMENT_NAME),
  "--patch-module" to UIOptionInfo(repeatTimes = 0, argumentName = "<module>=<file>(:<file>)*"),
  "--limit-modules" to UIOptionInfo(repeatTimes = 0, argumentName = "<module name>[,<module name>...]"),
  "--add-reads" to UIOptionInfo(repeatTimes = 0, argumentName = "<module>=<target-module>(,<target-module>)*"),
  "--add-exports" to UIOptionInfo(repeatTimes = 0, argumentName = MODULE_PACKAGE_TARGET_MODULE_ARGUMENT_NAME),
  "--finalization=" to UIOptionInfo(separator = "", argumentName = VALUE_ARGUMENT_NAME),
  "--illegal-access=" to UIOptionInfo(separator = "", argumentName = VALUE_ARGUMENT_NAME),
  "-ea" to UIOptionInfo(separator = ":", isArgumentOptional = true, argumentName = PACKAGE_AND_CLASS_ARGUMENT_NAME),
  "-da" to UIOptionInfo(separator = ":", isArgumentOptional = true, argumentName = PACKAGE_AND_CLASS_ARGUMENT_NAME),
  "-enableassertions" to UIOptionInfo(separator = ":", isArgumentOptional = true, argumentName = PACKAGE_AND_CLASS_ARGUMENT_NAME),
  "-disableassertions" to UIOptionInfo(separator = ":", isArgumentOptional = true, argumentName = PACKAGE_AND_CLASS_ARGUMENT_NAME),
  "-agentlib:" to UIOptionInfo(separator = "", argumentName = "<libname>[=<options>]"),
  "-agentpath:" to UIOptionInfo(separator = "", argumentName = "<pathname>[=<options>]"),
  "-javaagent:" to UIOptionInfo(separator = "", argumentName = "<jarpath>[=<options>]"),
  "-D" to UIOptionInfo(separator = "", repeatTimes = 0, argumentName = "<name>=<value>"),
  "-XX:" to UIOptionInfo(repeatTimes = 0),
  "--source" to UIOptionInfo(argumentName = "version")
 )

private data class UIOptionInfo(val separator: String? = null, @NlsSafe val argumentName: String? = null, val repeatTimes: Int = 1, val isArgumentOptional: Boolean = false)
private val DEFAULT_UI_OPTION_INSTANCE = UIOptionInfo()