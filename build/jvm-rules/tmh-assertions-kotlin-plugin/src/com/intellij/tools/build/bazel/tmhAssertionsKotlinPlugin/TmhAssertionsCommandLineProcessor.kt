// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * The options of the plugin. Each option takes `true` or `false`. An option that the compile does not set is false.
 * Each option corresponds to a system property of the jvm-inc-builder worker, which sets both to true in its process
 * for the Java output.
 */
@OptIn(ExperimentalCompilerApi::class)
class TmhAssertionsCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String
    get() = TmhAssertionsKotlinPlugin.PLUGIN_ID

  override val pluginOptions: Collection<AbstractCliOption>
    get() = listOf(ENABLED, GENERATE_LINE_NUMBERS)

  override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
    val key = when (option) {
      ENABLED -> ENABLED_KEY
      GENERATE_LINE_NUMBERS -> GENERATE_LINE_NUMBERS_KEY
      else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
    }
    val flag = value.toBooleanStrictOrNull()
               ?: throw CliOptionProcessingException("The option ${option.optionName} takes true or false, not: $value")
    configuration.put(key, flag)
  }

  companion object {
    /**
     * Turns the assertions on. The system property of the worker is `tmh.instrument.annotations`.
     */
    val ENABLED: CliOption = CliOption(
      optionName = "enabled",
      valueDescription = "true|false",
      description = "Add the soft assertions of the threading annotations to the generated classes",
      required = false,
      allowMultipleOccurrences = false,
    )

    /**
     * Gives each assertion call a line number. The system property of the worker is `tmh.generate.line.numbers`.
     */
    val GENERATE_LINE_NUMBERS: CliOption = CliOption(
      optionName = "generateLineNumbers",
      valueDescription = "true|false",
      description = "Give each assertion call the line number of its method",
      required = false,
      allowMultipleOccurrences = false,
    )

    val ENABLED_KEY: CompilerConfigurationKey<Boolean> =
      CompilerConfigurationKey.create("add the threading model assertions")

    val GENERATE_LINE_NUMBERS_KEY: CompilerConfigurationKey<Boolean> =
      CompilerConfigurationKey.create("generate line numbers for the assertion calls")
  }
}
