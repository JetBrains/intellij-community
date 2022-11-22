// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.createSmartPointer
import com.intellij.util.ReflectionUtil

internal abstract class CreateTargetAction<T : PsiElement>(
  target: T,
  @SafeFieldForPreview protected open val request: ActionRequest
) : IntentionAction, Cloneable {

  @SafeFieldForPreview
  private val myTargetPointer = target.createSmartPointer()

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return myTargetPointer.element != null && request.isValid
  }

  protected val target: T
    get() = requireNotNull(myTargetPointer.element) {
      "Don't access this property if isAvailable() returned false"
    }

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = target

  /**
   * This implementation clones current intention replacing [myTargetPointer]
   * field value with the pointer to the corresponding element
   * in the target file. It returns null if subclass has potentially unsafe fields not
   * marked with [@SafeFieldForPreview][SafeFieldForPreview].
   */
  override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
    // Check field safety in subclass
    if (super.getFileModifierForPreview(target) !== this) return null
    val oldElement: PsiElement = this.target
    if (target.originalFile != oldElement.containingFile) {
      throw IllegalStateException("Intention action ${this::class} ($familyName) refers to the element from another source file. " +
                                  "It's likely that it's going to modify a file not opened in the editor, " +
                                  "so default preview strategy won't work. Also, if another file is modified, " +
                                  "getElementToMakeWritable() must be properly implemented to denote the actual file " +
                                  "to be modified.")
    }
    val newElement = PsiTreeUtil.findSameElementInCopy(oldElement, target)
    val clone = try {
      super.clone() as CreateTargetAction<*>
    } catch (e: CloneNotSupportedException) {
      throw InternalError(e) // should not happen as we implement Cloneable
    }
    if (!ReflectionUtil.setField(
        CreateTargetAction::class.java, clone, SmartPsiElementPointer::class.java, "myTargetPointer",
        newElement.createSmartPointer()
      )) {
      return null
    }
    return clone
  }

  override fun startInWriteAction(): Boolean = true
}

internal abstract class CreateMemberAction(target: PsiClass, request: ActionRequest
) : CreateTargetAction<PsiClass>(target, request) {

  open fun getTarget(): JvmClass = target

}
