// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.VMOptionsService
import com.intellij.java.terminal.JavaShellCommandUtils.UIOptionInfo
import com.intellij.java.terminal.JavaShellCommandUtils.addClassPathOption
import com.intellij.java.terminal.JavaShellCommandUtils.addOptionsFromData
import com.intellij.java.terminal.JavaShellCommandUtils.classpathSuggestionsGenerator
import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellChildOptionsContext

internal class JavacShellCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return listOf(ShellCommandSpecInfo.create(getSpecs(), ShellCommandSpecConflictStrategy.REPLACE))
  }

  private fun getSpecs(): ShellCommandSpec = ShellCommandSpec("javac") {
    parserOptions(
      ShellCommandParserOptions.builder()
        .flagsArePosixNonCompliant(true)
        .optionsMustPrecedeArguments(true)
        .build()
    )

    description(JavaTerminalBundle.message("java.c.command.terminal.description"))

    dynamicOptions { terminalContext ->
      val javaContext = JavaShellCommandContext.create(terminalContext)
      val path = javaContext?.getJrePath()
      addJavacOptions(path)
    }


    val streamName: @Nls String = JavaTerminalBundle.message("output.stream.name")

    option("-?", "-help") {
      exclusiveOn(listOf("--help"))
      description(JavaTerminalBundle.message("java.command.terminal.help.option.description", streamName))
    }

    option("-X") {
      exclusiveOn(listOf("--help-extra"))
      description(JavaTerminalBundle.message("java.c.command.terminal.extra.options.command.description"))
    }

    option("-version") {
      exclusiveOn(listOf("--version"))
      description(JavaTerminalBundle.message("java.command.terminal.version.option.description", streamName))
    }

    addClassPathOption()

    argument {
      displayName(SOURCE_FILE_ARGUMENT_NAME)
      suggestions(ShellDataGenerators.fileSuggestionsGenerator())
    }
  }

  private suspend fun ShellChildOptionsContext.addJavacOptions(path: String?) {
    val optionsService = VMOptionsService.getInstance()
    val jdkOptionsData = withContext(Dispatchers.IO) {

      if (path == null) return@withContext null
      optionsService.getOrComputeOptionsForJavac(path).get()
    } ?: optionsService.getStandardJavacOptions()

    addOptionsFromData(jdkOptionsData, UI_OPTION_INFO_MAP)
  }
}

private const val SOURCE_FILE_ARGUMENT_NAME: @NlsSafe String = "[sourcefiles-or-classnames]"
private val UI_OPTION_INFO_MAP = mapOf(
  "-A" to UIOptionInfo(separator = "", argumentName = "key[=value]"),
  "--add-modules" to UIOptionInfo(argumentName = "module[,module]*"),
  "--boot-class-path" to UIOptionInfo(argumentName = "path", suggestionsGenerator = classpathSuggestionsGenerator()),
  "--class-path" to UIOptionInfo(argumentName = "path", suggestionsGenerator = classpathSuggestionsGenerator()),
  "-d" to UIOptionInfo(argumentName = "directory", suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator(true)),
  "-encoding" to UIOptionInfo(argumentName = "encoding"),
  "-endorseddirs" to UIOptionInfo(argumentName = "dirs"),
  "-extdirs" to UIOptionInfo(argumentName = "directories"),
  "-g:" to UIOptionInfo(separator = "", argumentName = "none|lines|vars"),
  "-h" to UIOptionInfo(argumentName = "directory", suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator(true)),
  "-implicit:" to UIOptionInfo(separator = "", argumentName = "none|class", isArgumentOptional = true),
  "-J" to UIOptionInfo(separator = "", argumentName = "option"),
  "--limit-modules" to UIOptionInfo(argumentName = "module[,module]*"),
  "--module" to UIOptionInfo(argumentName = "module-name"),
  "--module-path" to UIOptionInfo(argumentName = "path"),
  "--module-source-path" to UIOptionInfo(argumentName = "path"),
  "--module-version" to UIOptionInfo(argumentName = "version"),
  "-proc:" to UIOptionInfo(separator = "", argumentName = "none|only|full", isArgumentOptional = true),
  "-processor" to UIOptionInfo(argumentName = "class[,class]*"),
  "--processor-module-path" to UIOptionInfo(argumentName = "path", suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator()),
  "--processor-path" to UIOptionInfo(argumentName = "path" , suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator()),
  "-profile" to UIOptionInfo(argumentName = "profile"),
  "--release" to UIOptionInfo(argumentName = "release"),
  "-s" to UIOptionInfo(argumentName = "directory", suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator(true)),
  "--source" to UIOptionInfo(argumentName = "release"),
  "--source-path" to UIOptionInfo(argumentName = "path", suggestionsGenerator =  ShellDataGenerators.fileSuggestionsGenerator()),
  "--system" to UIOptionInfo(argumentName = "jdk"),
  "--target" to UIOptionInfo(argumentName = "release"),
  "--upgrade-module-path" to UIOptionInfo(argumentName = "path", suggestionsGenerator = ShellDataGenerators.fileSuggestionsGenerator()),
  "--add-exports" to UIOptionInfo(repeatTimes = 0, argumentName = "module/package=other-module(,other-module)*"),
  "--add-reads" to UIOptionInfo(repeatTimes = 0, argumentName = "module=other-module(,other-module)*"),
  "--default-module-for-created-files" to UIOptionInfo(argumentName = "module-name"),
  "--patch-module" to UIOptionInfo(repeatTimes = 0, argumentName = "module=path"),
  "-Xbootclasspath:" to UIOptionInfo(separator = "", argumentName = "path"),
  "-Xbootclasspath/a:" to UIOptionInfo(separator = "", argumentName = "path"),
  "-Xbootclasspath/p:" to UIOptionInfo(separator = "", argumentName = "path"),
  "-Xdiags:" to UIOptionInfo(separator = "", isArgumentOptional = true, argumentName = "compact|verbose"),
  "-Xdoclint:" to UIOptionInfo(separator = "", argumentName = "(none|all)"),
  "-Xdoclint/package:" to UIOptionInfo(separator = "", argumentName = "(none|all)"),
  "-Xlint:" to UIOptionInfo(separator = "", argumentName = "[-]key(,[-]key)*"),
  "-Xmaxerrs" to UIOptionInfo(argumentName = "number"),
  "-Xmaxwarns" to UIOptionInfo(argumentName = "number"),
  "-Xpkginfo:" to UIOptionInfo(separator = "", argumentName = "always|legacy|nonempty", isArgumentOptional = true),
  "-Xplugin:" to UIOptionInfo(separator = "", argumentName = "name"),
  "-Xprefer:" to UIOptionInfo(separator = "", argumentName = "source|newer", isArgumentOptional = true),
)