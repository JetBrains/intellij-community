// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.terminal.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider

class JavaShellCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return listOf(ShellCommandSpecInfo.create(getSpecs(), ShellCommandSpecConflictStrategy.REPLACE))
  }
}

private fun getSpecs(): ShellCommandSpec = ShellCommandSpec("java") {
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
      displayName(JavaTerminalBundle.message("java.command.terminal.classpath.option.argument.path.text"))
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