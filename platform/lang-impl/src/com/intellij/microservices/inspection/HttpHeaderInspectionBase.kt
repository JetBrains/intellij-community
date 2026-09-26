// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class HttpHeaderInspectionBase : LocalInspectionTool() {

  abstract fun getCustomHeadersEnabled(): Boolean

  abstract fun getCustomHeaders(): Set<String>

  abstract fun addCustomHeader(header: String)

  companion object {
    val HTTP_HEADER_INSPECTION: Key<HttpHeaderInspectionBase> = Key<HttpHeaderInspectionBase>("IncorrectHttpHeaderInspection")

    fun getCustomHeadersEnabled(context: PsiElement): Boolean = getHttpHeaderInspection(context)?.getCustomHeadersEnabled() ?: false

    fun getCustomHeaders(context: PsiElement): Set<String> = getHttpHeaderInspection(context)?.getCustomHeaders() ?: emptySet()

    private fun getHttpHeaderInspection(context: PsiElement): HttpHeaderInspectionBase? {
      val containingFile = context.containingFile.originalFile
      val profile = InspectionProjectProfileManager.getInstance(context.project).currentProfile

      return profile.getUnwrappedTool(HTTP_HEADER_INSPECTION, containingFile).asSafely<HttpHeaderInspectionBase>()
    }
  }
}