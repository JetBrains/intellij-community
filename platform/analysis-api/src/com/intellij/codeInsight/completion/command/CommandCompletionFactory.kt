// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Factory interface for creating and managing completion commands in a specific context.
 *
 * Provides functionality to determine applicability based on the file and offset,
 * retrieve a list of command providers, and define suffixes used during code completion.
 * Serves as a customization point for enhancing code completion workflows.
 *
 * Should implement DumbAware to support dumb mode
 */
interface CommandCompletionFactory : PossiblyDumbAware {
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
   * The default implementation returns a set of providers.
   * Language plugins can add command providers via 'com.intellij.codeInsight.completion.command.provider' EP
   * or implement [com.intellij.codeInsight.completion.command.ApplicableCompletionCommand]
   * and register with 'com.intellij.codeInsight.completion.applicable.command' language extension
   */
  fun commandProviders(project: Project, language: Language): List<CommandProvider> {
    return DumbService.getInstance(project).filterByDumbAwareness(EP_NAME.allForLanguageOrAny(language))
  }

  /**
   * Determines whether the functionality is applicable in the given context.
   *
   * @param psiFile the PSI file representing the file in which the applicability is being evaluated
   * @param offset the position within the file where the applicability should be checked
   * @return true if the functionality is applicable at the specified context, false otherwise
   */
  fun isApplicable(psiFile: PsiFile, offset: Int): Boolean {
    return true
  }

  /**
   * Creates a new file based on the provided original file and text content.
   *
   * @param originalFile The base file from which the new file will be created. It must be of type `PsiFile`.
   * @param text The text content to be used for the newly created file.
   * @return new file, containing text or null if creation file with [com.intellij.psi.PsiFileFactory.createFileFromText] is available
   *
   */
  fun createFile(originalFile: PsiFile, text: String): PsiFile? = null

  /**
   * Determines whether the functionality supports filtering with a double prefix.
   * If it doesn't support other items (non-command completion) will be not filtered out.
   *
   * @return true if double prefix filtering is supported, false otherwise
   */
  fun supportFiltersWithDoublePrefix(): Boolean = true

  /**
   * Determines whether the command completion process should prioritize this specific action based on the given parameters.
   * It will be placed at the first items.
   * It only applies if it is auto-popup completion and contains a full-suffix
   *
   * @param parameters the completion parameters containing the context and state for the current completion process
   * @return true if the action should be prioritized during the completion process, false otherwise
   */
  fun forcePrioritize(parameters: CompletionParameters) : Boolean = parameters.process.isAutopopupCompletion
}

private val EP_NAME = LanguageExtension<CommandProvider>("com.intellij.codeInsight.completion.command.provider")
