// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.AutoCompletionPolicy.NEVER_AUTOCOMPLETE
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Represents a specialized lookup element intended for usage in command completion scenarios.
 */
@ApiStatus.Internal
internal class CommandCompletionLookupElement(
  lookupElement: LookupElement,
  val command: CompletionCommand,
  val hostStartOffset: Int,
  val suffix: String,
  val icon: Icon?,
  val highlighting: HighlightInfoLookup?,
  val useLookupString: Boolean = true,
) : LookupElementDecorator<LookupElement>(lookupElement) {
  override fun isWorthShowingInAutoPopup(): Boolean {
    return true
  }

  override fun getAutoCompletionPolicy(): AutoCompletionPolicy? {
    return NEVER_AUTOCOMPLETE
  }

  internal val hasPreview: Boolean = command is CompletionCommandWithPreview

  internal val preview: IntentionPreviewInfo? by lazy {
    (command as? CompletionCommandWithPreview)?.getPreview()
  }
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
@ApiStatus.Internal
fun getDataContext(
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
@ApiStatus.Internal
fun getTargetContext(offset: Int, editor: Editor): PsiElement? {
  try {
    val util = TargetElementUtil.getInstance()
    return util.findTargetElement(editor, util.getReferenceSearchFlags(), offset)
  }
  catch (_: IndexNotReadyException) {
    return null
  }
}