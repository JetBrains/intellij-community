// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity

import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider.CommandLine
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.UsefulTestCase

abstract class RunAnythingCommandLineProviderTestCase : UsefulTestCase() {
  private val HELP_COMMAND = "start"

  private lateinit var helpCommand: String
  private lateinit var helpCommandAliases: List<String>

  override fun setUp() {
    super.setUp()
    helpCommand = HELP_COMMAND
    helpCommandAliases = emptyList()
  }

  fun withHelpCommands(helpCommand: String, vararg aliases: String, action: () -> Unit) {
    this.helpCommand = helpCommand
    helpCommandAliases = aliases.toList()
    try {
      action()
    }
    finally {
      this.helpCommand = HELP_COMMAND
      helpCommandAliases = emptyList()
    }
  }

  fun getValuesFor(command: String, vararg variants: String, prefix: String = helpCommand): List<String> {
    val provider = object : RunAnythingCommandLineProvider() {
      override fun getHelpCommand() = this@RunAnythingCommandLineProviderTestCase.helpCommand

      override fun getHelpCommandAliases() = helpCommandAliases

      override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine) =
        variants.asSequence()

      override fun run(dataContext: DataContext, commandLine: CommandLine) = true
    }
    val values = provider.getValues(DataContext.EMPTY_CONTEXT, "$prefix $command")
    assertTrue(values.all { it.startsWith("$prefix ") })
    return values.map { it.removePrefix("$prefix ") }
  }

  fun withCommandLineFor(command: String, action: (CommandLine) -> Unit) {
    var isSuggestingTouched = false
    var isRunningTouched = false
    val provider = object : RunAnythingCommandLineProvider() {
      override fun getHelpCommand() = this@RunAnythingCommandLineProviderTestCase.helpCommand

      override fun getHelpCommandAliases() = helpCommandAliases

      override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
        action(commandLine)
        isSuggestingTouched = true
        return emptySequence()
      }

      override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
        action(commandLine)
        isRunningTouched = true
        return true
      }
    }
    provider.getValues(DataContext.EMPTY_CONTEXT, command)
    provider.execute(DataContext.EMPTY_CONTEXT, command)
    assertTrue(isSuggestingTouched)
    assertTrue(isRunningTouched)
  }

  fun assertMatchingValue(commandLine: String, expected: String?) {
    val provider = object : RunAnythingCommandLineProvider() {
      override fun getHelpCommand() = this@RunAnythingCommandLineProviderTestCase.helpCommand

      override fun getHelpCommandAliases() = helpCommandAliases

      override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
        return emptySequence()
      }

      override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
        return true
      }
    }
    assertEquals(expected, provider.findMatchingValue(DataContext.EMPTY_CONTEXT, commandLine))
  }
}