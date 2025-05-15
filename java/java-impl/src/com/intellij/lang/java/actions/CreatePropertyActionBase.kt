// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.toNotNull
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTreeUtil

internal abstract class CreatePropertyActionBase(
  target: PsiClass,
  override val request: CreateMethodRequest
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  override fun getFamilyName(): String = QuickFixBundle.message("create.property.from.usage.family")

  private fun doGetPropertyInfo() = PropertyUtilBase.getPropertyNameAndKind(request.methodName)

  override fun isAvailable(project: Project, file: PsiFile, target: PsiClass): Boolean {
    if (!super.isAvailable(project, file, target)) return false

    val accessorName = request.methodName
    if (!PsiNameHelper.getInstance(project).isIdentifier(accessorName)) return false

    val (propertyName: String, propertyKind: PropertyKind) = doGetPropertyInfo() ?: return false
    if (propertyName.isNullOrEmpty() || propertyKind == null) return false

    // check parameters count
    when (propertyKind) {
      PropertyKind.GETTER, PropertyKind.BOOLEAN_GETTER -> if (request.expectedParameters.isNotEmpty()) return false
      PropertyKind.SETTER -> if (request.expectedParameters.size != 1) return false
    }

    return target.findMethodsByName(request.methodName, false).isEmpty()
  }

  protected val propertyInfo: Pair<String, PropertyKind> get() = requireNotNull(doGetPropertyInfo()).toNotNull()

  internal fun getPropertyName() : String? = doGetPropertyInfo()?.first

  override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { propertyInfo.first }

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val copyClass = PsiTreeUtil.findSameElementInCopy(target, psiFile)
    createRenderer(project, copyClass).doRender()
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, file: PsiFile, target: PsiClass) {
    createRenderer(project, target).doRender()
  }

  abstract fun createRenderer(project: Project, targetClass: PsiClass): PropertyRenderer
}
