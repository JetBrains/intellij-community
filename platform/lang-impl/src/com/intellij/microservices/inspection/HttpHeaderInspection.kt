// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.inspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.http.HttpHeadersDictionary
import com.intellij.microservices.inspection.HttpHeaderInspectionBase.Companion.HTTP_HEADER_INSPECTION
import com.intellij.microservices.inspection.HttpHeaderInspectionBase.Companion.getCustomHeadersEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface HttpHeaderInspection {

  fun isCustomHeader(header: String, psiElement: PsiElement): Boolean

  fun getCustomHttpHeaderFix(psiElement: PsiElement, header: String): LocalQuickFix? {
    return if (getCustomHeadersEnabled(psiElement) && header.isNotBlank()) AddCustomHttpHeaderIntentionAction(header)
    else null
  }

  fun checkHeader(holder: ProblemsHolder, header: String, psiElement: PsiElement) {
    val customHeader = isCustomHeader(header, psiElement)

    val existingHeader = customHeader || HttpHeadersDictionary.getHeaders().keys.any { StringUtil.equalsIgnoreCase(header, it) }

    if (!existingHeader) {
      holder.registerProblem(psiElement,
                             MicroservicesBundle.message("inspection.incorrect.http.header.unknown.header"),
                             ProblemHighlightType.WEAK_WARNING,
                             *LocalQuickFix.notNullElements(getCustomHttpHeaderFix(psiElement, header)))
    }
  }

  class AddCustomHttpHeaderIntentionAction(private val header: String) : LocalQuickFix {
    override fun getFamilyName(): String = MicroservicesBundle.message("inspection.incorrect.http.header.add.custom")

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      return IntentionPreviewInfo.Html(MicroservicesBundle.message("inspection.incorrect.http.header.add.custom.description"))
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile

      currentProfile.modifyToolSettings(HTTP_HEADER_INSPECTION, descriptor.psiElement) { tool -> tool.addCustomHeader(header) }
    }
  }
}