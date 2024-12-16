// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Factory interface for creating and managing completion commands in a specific context.
 *
 * Provides functionality to determine applicability based on the file and offset,
 * retrieve a list of command providers, and define suffixes used during code completion.
 * Serves as a customization point for enhancing code completion workflows.
 *
 * Should implement DumbAware to support dumb mode
 */
@ApiStatus.Experimental
interface CommandCompletionFactory {
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
   * Retrieves a list of command providers responsible for supplying completion commands.
   */
  fun commandProviders(project: Project): List<CommandProvider>

  /**
   * Determines whether the functionality is applicable in the given context.
   *
   * @param psiFile the PSI file representing the file in which the applicability is being evaluated
   * @param offset the position within the file where the applicability should be checked
   * @return true if the functionality is applicable at the specified context, false otherwise
   */
  fun isApplicable(psiFile: PsiFile, offset: Int): Boolean
}