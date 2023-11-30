// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.*
import com.intellij.psi.PsiElement

val RELATED_LOCATIONS = Key.create<List<ProblemRelatedLocation>>("RELATED_LOCATIONS")

class ProblemRelatedLocation(startElement: PsiElement,
                             endElement: PsiElement,
                             @InspectionMessage message: String) {
  private val problemDescriptor: ProblemDescriptorBase = ProblemDescriptorBase(
    startElement,
    endElement,
    message,
    emptyArray(),
    ProblemHighlightType.INFORMATION,
    false,
    null,
    false,
    false)

  fun getRange(): TextRange? = problemDescriptor.textRange
  fun getPsiElement(): PsiElement? = problemDescriptor.psiElement
  fun getLineNumber(): Int = problemDescriptor.lineNumber

  fun getOffset(): Int? {
    val startOffset = getRange()?.startOffset ?: return null
    return startOffset - problemDescriptor.getLineStartOffset()
  }

}

class ProblemDescriptorBaseWithUserData(pd: ProblemDescriptorBase, userData: UserDataHolderEx)
  : ProblemDescriptorBase(
  pd.startElement,
  pd.endElement,
  pd.descriptionTemplate,
  pd.fixes,
  pd.highlightType,
  pd.isAfterEndOfLine,
  pd.textRangeInElement,
  pd.showTooltip(),
  pd.isOnTheFly,
),
    UserDataHolderEx by userData

class ProblemDescriptorWithUserData(pd: ProblemDescriptor, userData: UserDataHolderEx)
  : ProblemDescriptor by pd,
    UserDataHolderEx by userData

fun ProblemDescriptor.withRelatedLocations(locations: List<ProblemRelatedLocation>): ProblemDescriptor {
  when (this) {
    is UserDataHolder -> {
      this.putUserData(RELATED_LOCATIONS, locations)
      return this
    }
    is ProblemDescriptorBase -> {
      val problemDescriptor = ProblemDescriptorBaseWithUserData(this, UserDataHolderBase())
      problemDescriptor.putUserData(RELATED_LOCATIONS, locations)
      return problemDescriptor
    }
    else -> {
      val problemDescriptor = ProblemDescriptorWithUserData(this, UserDataHolderBase())
      problemDescriptor.putUserData(RELATED_LOCATIONS, locations)
      return problemDescriptor
    }
  }
}
