// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents an abstract base class for defining commands in a code completion system.
 * Commands are components that can be executed within a specific context during code completion.
 * Should be stateless
 *
 * This class is marked as experimental and may change in future releases.
 */
abstract class CompletionCommand {
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
  open val additionalInfo: String? = null
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

  /**
   * Retrieves the PSI (Program Structure Interface) element at the specified offset within the given PSI file.
   * This method finds the appropriate context element, which can be used for further analysis or execution.
   *
   * @param offset the position within the file at which to locate the PSI element.
   * @param psiFile the PSI file object in which the offset is located.
   * @return the PSI element at the specified offset, or null if no element is found.
   */
  open fun getContext(offset: Int, psiFile: PsiFile): PsiElement? {
    return (if (offset == 0) psiFile.findElementAt(offset) else psiFile.findElementAt(offset - 1))
  }
}

/**
 * Represents an abstract base class for completion commands that are applicable under specific conditions.
 * This class extends the functionality of `CompletionCommand` and introduces methods to evaluate the
 * applicability of the command in a given context.
 *
 * Should be stateless and can be applied either to physical and non-physical classes
 * Should implement DumbAware to support DumbMode
 */
abstract class ApplicableCompletionCommand : CompletionCommand(), PossiblyDumbAware {

  /**
   * Determines whether the command is applicable based on the given context.
   * Can be called on non-physical classes and imaginary editors
   *
   * @param offset The offset in the file where the applicability should be checked.
   * @param psiFile The PSI file where the applicability should be evaluated.
   * @param editor The editor. Can be null. Used only for compatibility with old actions
   */
  @RequiresReadLock
  abstract fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean

  /**
   * Indicates whether the implementation supports non-written files.
   * (For example, a command can navigate to another file)
   *
   * @return true if non-written files are supported; false otherwise.
   */
  open fun supportsReadOnly(): Boolean = false
}

data class HighlightInfoLookup(
  val range: TextRange,
  val attributesKey: TextAttributesKey,
  val priority: Int, //higher is on the top
)
