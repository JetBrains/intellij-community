// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.VMOption
import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellChildOptionsContext

class JavaShellCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return listOf(ShellCommandSpecInfo.create(getSpecs(), ShellCommandSpecConflictStrategy.REPLACE))
  }
}

private fun getSpecs(): ShellCommandSpec = ShellCommandSpec("java") {
  dynamicOptions { context ->
    val path = context.getJrePath() ?: return@dynamicOptions
    val jdkOptionsData = withContext(Dispatchers.IO) {
      VMOptionsService.getInstance().getOrComputeOptionsForJdk(path).get() ?: return@withContext null
    } ?: return@dynamicOptions
    val optionByVariant = jdkOptionsData.options.filter { it.kind == VMOptionKind.Standard || it.kind == VMOptionKind.Product }.groupBy { it.variant }
    addOptionsFromVM(optionByVariant[VMOptionVariant.X])
  }
  description(JavaTerminalBundle.message("java.command.terminal.description"))
  option("--help", "-help", "-h") {
    description(JavaTerminalBundle.message("java.command.terminal.help.option.description"))
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
  option("--version", "-version") {
    description(JavaTerminalBundle.message("java.command.terminal.version.option.description"))
  }
  option("-classpath", "-cp") {
    description(JavaTerminalBundle.message("java.command.terminal.classpath.option.description"))
    argument {
      displayName(JavaTerminalBundle.message("java.command.terminal.classpath.option.argument.path.text", ShellCommandUtils.getClassPathSeparator()))
    }
  }
  option("-showversion", "--show-version") {
    description(JavaTerminalBundle.message("java.command.terminal.show.version.option.description"))
  }
  option("--dry-run") {
    description(JavaTerminalBundle.message("java.command.terminal.dry.run.option.description"))
  }

  argument {
    displayName(JavaTerminalBundle.message("java.command.terminal.argument.main.class.text"))
  }
}

private fun ShellChildOptionsContext.addOptionsFromVM(optionList: List<VMOption>?) {
  if (optionList == null) return
  optionList.forEach {
    val presentableName = "${it.variant.prefix()}${it.optionName}"
    option(presentableName) {
      repeatTimes = 1

      if (JavaTerminalBundle.isMessageInBundle(it.optionName.getXOptionBundleKey())) {
        description(JavaTerminalBundle.message(it.optionName.getXOptionBundleKey()))
      }

      if (!KNOWN_X_OPTIONS_WITH_ARGUMENT.contains(presentableName)) return@option
      separator = it.variant.suffix()?.toString() ?: ""
      argument {
        displayName(JavaTerminalBundle.message("java.command.terminal.default.argument.text"))
      }
    }
  }
}

private val KNOWN_X_OPTIONS_WITH_ARGUMENT = setOf(
  "-Xms",
  "-Xmx",
  "-Xmn",
  "-Xss",
  "-Xbootclasspath/a:",
  "-Xlog:",
  "-Xloggc:",
)

private suspend fun ShellRuntimeContext.getJrePath(): String? {
  val result = runShellCommand("java -XshowSettings:properties -version")
  if (result.exitCode != 0) return null
  return result.output.split('\n').map { it.trim() }.find { it.startsWith("java.home = ") }?.split(" = ")?.getOrNull(1)
}

private fun String.getXOptionBundleKey() : String {
  val name = this.replace(':', '.').trim('.')
  return "java.command.terminal.$name.option.description"
}