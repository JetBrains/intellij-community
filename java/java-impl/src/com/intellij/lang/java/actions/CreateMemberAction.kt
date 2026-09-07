// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public abstract class CreateTargetAction<T : PsiElement>(
  target: T,
  @SafeFieldForPreview protected open val request: ActionRequest
) : LocalQuickFixAndIntentionActionOnPsiElement(target) {
  @Suppress("UNCHECKED_CAST")
  protected val target: T get() = startElement as T

  final override fun isAvailable(project: Project, psiFile: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement): Boolean {
    return isAvailable(project, psiFile, target)
  }

  final override fun isAvailable(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return isAvailable(project, psiFile, target)
  }

  public open fun isAvailable(project: Project, file: PsiFile, target: T): Boolean {
    return request.isValid
  }

  final override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    invoke(project, psiFile, target)
  }

  final override fun invoke(project: Project, psiFile: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    invoke(project, psiFile, target)
  }

  public abstract fun invoke(project: Project, file: PsiFile, target: T)

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = target
}

public abstract class CreateMemberAction(target: PsiClass, request: ActionRequest) : CreateTargetAction<PsiClass>(target, request) {

  public open fun getTarget(): JvmClass = target
}

/**
 * A base class for an action which adds a member to [target].
 *
 * The action writes into the file of [target], which is often not the file of the call site.
 * [PsiUpdateModCommandAction] handles this: it starts the update on the target element, so the
 * writable copy, the caret and the template all belong to the target file.
 */
@ApiStatus.Internal
public abstract class CreateMemberModCommandAction(
  target: PsiClass,
  protected open val request: ActionRequest,
) : PsiUpdateModCommandAction<PsiClass>(target) {

  private val targetPointer = target.createSmartPointer()

  /**
   * The physical target class, or null when it is gone.
   * Use it to compute an anchor before the update starts. Inside
   * [invoke][PsiUpdateModCommandAction.invoke] use the writable copy instead.
   */
  public val targetClass: PsiClass? get() = targetPointer.element

  /**
   * @return the full action text, e.g. *Create method 'foo' in 'SomeClass'*
   */
  protected abstract fun getText(target: PsiClass): @IntentionName String

  /**
   * An extra availability check. The caller already checked [ActionRequest.isValid].
   */
  protected open fun isAvailable(target: PsiClass): Boolean = true

  protected open val priority: PriorityAction.Priority get() = PriorityAction.Priority.NORMAL

  override fun getPresentation(context: ActionContext, element: PsiClass): Presentation? {
    if (!request.isValid || !isAvailable(element)) return null
    return Presentation.of(getText(element)).withPriority(priority)
  }
}
