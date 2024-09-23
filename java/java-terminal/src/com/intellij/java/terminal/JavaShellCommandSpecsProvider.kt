// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.openapi.diagnostic.Logger
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
    dynamicOptions { terminalContext ->
      val javaContext = JavaShellCommandContext.create(terminalContext) ?: return@dynamicOptions
      addOptionsFromVM(javaContext.getJrePath())
      val version = javaContext.getJavaVersion() ?: return@dynamicOptions
      if (version.isAtLeast(11)) {
        addDoubleDashedOptions()
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
      }
      description(JavaTerminalBundle.message("java.command.terminal.jar.option.description"))
    }
    option("-D") {
      description(JavaTerminalBundle.message("java.command.terminal.D.option.description"))
      repeatTimes = 0
      separator = ""
      argument {
        displayName(JavaTerminalBundle.message("java.command.terminal.D.option.argument.key.text"))
      }
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
    if (path == null) return
    val jdkOptionsData = withContext(Dispatchers.IO) {
      VMOptionsService.getInstance().getOrComputeOptionsForJdk(path).get() ?: return@withContext null
    } ?: return
    val variantMap = jdkOptionsData.options
      .filter { (it.kind == VMOptionKind.Standard || it.kind == VMOptionKind.Product) }
      .groupBy { it.variant }
    variantMap[VMOptionVariant.X]?.forEach {
      val presentableName = "${it.variant.prefix()}${it.optionName}"
      option(presentableName) {
        repeatTimes = 1

        if (JavaTerminalBundle.isMessageInBundle(getOptionBundleKey(it.optionName))) {
          description(JavaTerminalBundle.message(getOptionBundleKey(it.optionName)))
        }
        else {
          LOG.warn("Unknown X option: \"${it.optionName}\". Paste following string into the JavaTerminalBundle.properties:\n" +
                   "${getOptionBundleKey(it.optionName)}=${it.doc}")
        }

        if (!KNOWN_OPTIONS_WITH_EMPTY_SEPARATOR.contains(presentableName)) return@option
        separator = it.variant.suffix()?.toString() ?: ""
        argument {
          displayName(JavaTerminalBundle.message("java.command.terminal.default.argument.text"))
        }
      }
    }
    variantMap[VMOptionVariant.DASH_DASH]?.forEach {
      val optionName = it.optionName
      val presentableName = "${it.variant.prefix()}$optionName"
      option(presentableName) {
        if (!JavaTerminalBundle.isMessageInBundle(getOptionBundleKey(optionName))) {
          LOG.warn("Unknown DashDash option: \"$optionName\". Provide ${getOptionBundleKey(optionName)} and ${getOptionArgumentBundleKey(optionName)}")
          return@option
        }
        description(JavaTerminalBundle.message(getOptionBundleKey(optionName)))
        if (!JavaTerminalBundle.isMessageInBundle(getOptionArgumentBundleKey(optionName))) return@option
        if (KNOWN_REPETITIVE_OPTIONS.contains(presentableName)) repeatTimes = 0
        if (KNOWN_OPTIONS_WITH_EMPTY_SEPARATOR.contains(presentableName)) separator = ""
        argument {
          displayName(JavaTerminalBundle.message(getOptionArgumentBundleKey(optionName)))
        }
      }
    }
  }

  private fun ShellChildOptionsContext.addDoubleDashedOptions() {
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
  }

  private fun getOptionBundleKey(option: String): String = "java.command.terminal.${getCanonicalOptionName(option)}.option.description"

  private fun getOptionArgumentBundleKey(option: String): String = "java.command.terminal.${getCanonicalOptionName(option)}.option.argument.text"

  private fun getCanonicalOptionName(option: String): String = option.replace(Regex("[:|\\-]"), ".").trim('=', '.')
}

private val KNOWN_OPTIONS_WITH_EMPTY_SEPARATOR = setOf(
  "-Xms",
  "-Xmx",
  "-Xmn",
  "-Xss",
  "-Xbootclasspath:",
  "-Xbootclasspath/a:",
  "-Xbootclasspath/p:",
  "-Xlog:",
  "-Xloggc:",
  "--finalization=",
  "--illegal-access="
)

private val KNOWN_REPETITIVE_OPTIONS = setOf(
  "--add-opens",
  "--patch-module",
  "--limit-modules",
  "--add-reads",
  "--add-exports",
)

private val LOG = Logger.getInstance(JavaShellCommandSpecsProvider::class.java)