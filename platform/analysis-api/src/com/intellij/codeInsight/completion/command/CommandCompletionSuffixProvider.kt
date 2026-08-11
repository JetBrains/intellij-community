// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import org.jetbrains.annotations.ApiStatus

/**
 * The PSI-independent part of the command completion contract: which characters trigger command completion
 * and how the lookup is filtered afterwards.
 *
 * [CommandCompletionFactory] extends this interface, so a language that registers a factory in a backend module
 * gets the suffixes only there. The lookup itself lives on the frontend in remote development, and char filters,
 * lookup filters and hints have to know the suffixes without any backend PSI, therefore this part is a separate
 * language extension 'com.intellij.codeInsight.completion.command.suffixProvider', which should be registered
 * in a module loaded on the frontend.
 */
@ApiStatus.Experimental
interface CommandCompletionSuffixProvider {
  /**
   * Provides the default character suffix. After that, suffix command completion will be enabled
   *
   * @return The character suffix, which is '.'.
   */
  fun suffix(): Char = '.'

  /**
   * Determines the character suffix to filter only command lookup
   */
  fun filterSuffix(): Char? = '.'

  /**
   * Determines whether the functionality supports filtering with a double prefix.
   * If it doesn't support other items (non-command completion) will be not filtered out.
   *
   * @return true if double prefix filtering is supported, false otherwise
   */
  fun supportFiltersWithDoublePrefix(): Boolean = true
}
