// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.commands.core.HighlightInfoLookup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents an abstract base class for defining commands in a code completion system.
 * Commands are components that can be executed within a specific context during code completion.
 * Should be stateless
 *
 * This class is marked as experimental and may change in future releases.
 */
@ApiStatus.Experimental
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
   * @param editor the editor instance where the file is being edited. Can be null. Used only for compatibility with old actions
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
@ApiStatus.Experimental
abstract class ApplicableCompletionCommand : CompletionCommand() {
  companion object {

    val EP_NAME = ExtensionPointName<ApplicableCompletionCommand>("com.intellij.codeInsight.completion.command")
  }

  /**
   * Determines whether the command is applicable based on the given context.
   * Can be called on non-physical classes
   *
   * @param offset The offset in the file where the applicability should be checked.
   * @param psiFile The PSI file where the applicability should be evaluated.
   * @param editor The editor. Can be null. Used only for compatibility with old actions
   */
  @RequiresReadLock
  abstract fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean
  open fun supportNonWrittenFiles(): Boolean = false
}

/**
 * Constructs a [DataContext] instance using the specified parameters, including the PSI file,
 * editor, and optionally a context element. The resulting data context is populated with
 * relevant keys and values to be used within the context of the provided elements.
 *
 * @param psiFile The [PsiFile] associated with the current context.
 * @param editor The [Editor] instance corresponding to the current context.
 * @param context An optional [PsiElement] representing the context; can be null.
 * @return A [DataContext] instance containing the provided context information.
 */
internal fun getDataContext(
  psiFile: PsiFile,
  editor: Editor,
  context: PsiElement?,
): DataContext {
  val dataContext = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, psiFile.project)
    .add(CommonDataKeys.EDITOR, editor)
    .add(CommonDataKeys.PSI_ELEMENT, context)
    .add(CommonDataKeys.PSI_FILE, psiFile)
    .add(LangDataKeys.CONTEXT_LANGUAGES, arrayOf(psiFile.language))
    .build()
  return dataContext
}

/**
 * Retrieves the PSI element located at the specified offset within the editor's context.
 * This method attempts to find a target PSI element based on the editor's state and the offset,
 * allowing interaction with the underlying code structure.
 *
 * @param offset The offset in the editor's document where the target element is to be located.
 * @param editor The editor instance in which the method searches for the target element.
 * @return The PSI element found at the specified offset, or null if no suitable element is found
 *         or if the index is not ready.
 */
internal fun getTargetContext(offset: Int, editor: Editor): PsiElement? {
  try {
    val util = TargetElementUtil.getInstance()
    return util.findTargetElement(editor, util.getReferenceSearchFlags(), offset)
  }
  catch (_: IndexNotReadyException) {
    return null
  }
}