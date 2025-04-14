// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents an abstract base class for defining commands in a code completion system.
 * Commands are components that can be executed within a specific context during code completion.
 * Should be stateless
 *
 * This class is marked as experimental and may change in future releases.
 */
abstract class CompletionCommand : UserDataHolderBase() {
  /**
   * Represents the name which is used as a main lookup string
   */
  abstract val name: String

  /**
   * Represents a localized, human-readable name for the command, used in tail lookup string
   */
  abstract val i18nName: @Nls String
  abstract val icon: Icon?

  /**
   * Defines the priority of the command in the code completion system.
   *
   * A higher numerical value indicates a higher priority, while a lower value
   * suggests lesser importance. Commands with higher priority are likely to
   * appear prominently in the completion suggestions list. This property
   * can be left null, in which case a default priority will be assumed.
   */
  open val priority: Int? = null

  /**
   * Provides additional information about the command that can be displayed
   * in the completion popup. This information helps users understand the
   * purpose or effect of the command.
   */
  open val additionalInfo: String? = null

  /**
   * Specifies how the command should be highlighted in the editor.
   * Contains information about the text range to highlight, the highlighting style,
   * and the priority of the highlight effect.
   */
  open val highlightInfo: HighlightInfoLookup? = null

  /**
   * Executes a specific operation based on the provided parameters.
   * Can be called on non-physical files.
   * Will be called on EDT thread without write or read access
   *
   * @param offset the position within the file where the operation should be performed
   * @param psiFile the PsiFile object representing the file in which the operation is executed
   * @param editor the editor instance where the file is being edited.
   * It can be a null or imaginary editor.
   * Used only for compatibility with old actions
   */
  @RequiresEdt
  abstract fun execute(offset: Int, psiFile: PsiFile, editor: Editor?)

  override fun toString(): String {
    return "CompletionCommand(name='$name', class='${this::class.simpleName}')"
  }


  /**
   * Retrieves a custom prefix matcher for the completion command.
   * Returns the PrefixMatcher instance used for matching completion prefixes, or null if none is available.
   *
   * @return the custom PrefixMatcher for completion command or null if not available.
   */
  open fun customPrefixMatcher(prefix: String): PrefixMatcher? = null


  /**
   * Retrieves a list of synonyms.
   * They are used for lookup search, but are not shown in the lookup list.
   * @return a list of strings representing synonyms. If no synonyms are found, returns an empty list.
   */
  open val synonyms: List<String> = emptyList()
}

/**
 * Retrieves the PSI (Program Structure Interface) element at the specified offset within the given PSI file.
 * This method finds the appropriate context element, which can be used for further analysis or execution.
 *
 * @param offset the position within the file at which to locate the PSI element.
 * @param psiFile the PSI file object in which the offset is located.
 * @return the PSI element at the specified offset, or null if no element is found.
 */
fun getCommandContext(offset: Int, psiFile: PsiFile): PsiElement? {
  return (if (offset == 0) psiFile.findElementAt(offset) else psiFile.findElementAt(offset - 1))
}

data class HighlightInfoLookup(
  val range: TextRange,
  val attributesKey: TextAttributesKey,
  val priority: Int, //higher is on the top
)


/**
 * Represents a command for code completion with a preview feature.
 */
interface CompletionCommandWithPreview {
  fun getPreview(): IntentionPreviewInfo?
}