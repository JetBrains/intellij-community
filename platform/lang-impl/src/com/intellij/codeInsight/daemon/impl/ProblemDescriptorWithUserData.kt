// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

val RELATED_LOCATIONS = Key.create<List<ProblemRelatedLocation>>("RELATED_LOCATIONS")
val RELATED_PROBLEMS_ROOT_HASH = Key.create<String>("RELATED_PROBLEMS_ROOT_HASH")
val RELATED_PROBLEMS_CHILD_HASH = Key.create<String>("RELATED_PROBLEMS_CHILD_HASH")
val PROBLEM_DESCRIPTOR_TAG = Key.create<List<String>>("PROBLEM_DESCRIPTOR_TAG")

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

fun ProblemDescriptor.withRelatedLocations(locations: List<ProblemRelatedLocation>): ProblemDescriptor =
  withUserData { putUserData(RELATED_LOCATIONS, locations) }


@ApiStatus.Internal
class ProblemDescriptorBaseWithUserData(
  private val pd: ProblemDescriptorBase,
  userData: UserDataHolderEx
) : ProblemDescriptorBase(
  pd.startElement,
  pd.endElement,
  pd.descriptionTemplate,
  pd.fixes,
  pd.highlightType,
  pd.isAfterEndOfLine,
  pd.textRangeInElement,
  pd.showTooltip(),
  pd.isOnTheFly,
), UserDataHolderEx by userData {
  // Override all methods to delegate to possibly overridden pd implementation
  override fun getDescriptionTemplate(): String = pd.getDescriptionTemplate()
  override fun isOnTheFly(): Boolean = pd.isOnTheFly
  override fun getPsiElement(): PsiElement = pd.getPsiElement()
  override fun getTextRangeInElement(): TextRange? = pd.textRangeInElement
  override fun getStartElement(): PsiElement = pd.startElement
  override fun getEndElement(): PsiElement = pd.endElement
  override fun getProject(): Project = pd.project
  override fun getLineNumber(): Int = pd.getLineNumber()
  override fun getLineStartOffset(): Int = pd.getLineStartOffset()
  override fun getHighlightType(): ProblemHighlightType = pd.highlightType
  override fun isAfterEndOfLine(): Boolean = pd.isAfterEndOfLine
  override fun getEnforcedTextAttributes(): TextAttributesKey = pd.enforcedTextAttributes
  override fun setTextAttributes(key: TextAttributesKey?) {
    pd.setTextAttributes(key)
  }
  override fun getTextRangeForNavigation(): TextRange? = pd.getTextRangeForNavigation()
  override fun getTextRange(): TextRange? = pd.getTextRange()
  override fun getNavigatable(): Navigatable = pd.navigatable
  override fun getContainingFile(): VirtualFile? = pd.containingFile
  override fun setNavigatable(navigatable: Navigatable?) {
    pd.navigatable = navigatable
  }
  override fun getProblemGroup(): ProblemGroup? = pd.problemGroup
  override fun setProblemGroup(problemGroup: ProblemGroup?) {
    pd.problemGroup = problemGroup
  }
  override fun showTooltip(): Boolean = pd.showTooltip()
  override fun toString(): String = pd.toString()
  override fun getFixes(): Array<LocalQuickFix>? = pd.fixes
  override fun getTooltipTemplate(): String = pd.tooltipTemplate
  override fun getDescriptorForPreview(target: PsiFile): ProblemDescriptor = pd.getDescriptorForPreview(target)
}

@ApiStatus.Internal
class ProblemDescriptorWithUserData(private val pd: ProblemDescriptor, userData: UserDataHolderEx)
  : ProblemDescriptor by pd, UserDataHolderEx by userData {

  // manually delegate to pd because https://youtrack.jetbrains.com/issue/KT-18324
  override fun getTooltipTemplate(): String = pd.tooltipTemplate
  override fun getDescriptorForPreview(target: PsiFile): ProblemDescriptor = pd.getDescriptorForPreview(target)
}

@ApiStatus.Internal
class CommonProblemDescriptorWithUserData(cpd: CommonProblemDescriptor, userData: UserDataHolderEx) :
  CommonProblemDescriptor by cpd, UserDataHolderEx by userData

fun CommonProblemDescriptor.withUserDataCommon(f: UserDataHolderEx.() -> Unit): CommonProblemDescriptor = when (this) {
  is ProblemDescriptor -> withUserData(f)
  is UserDataHolderEx -> apply(f)
  else -> CommonProblemDescriptorWithUserData(this, UserDataHolderBase()).apply(f)
}

fun ProblemDescriptor.withUserData(f: UserDataHolderEx.() -> Unit): ProblemDescriptor = when (this) {
  is UserDataHolderEx -> apply(f)
  is ProblemDescriptorBase -> ProblemDescriptorBaseWithUserData(this, UserDataHolderBase()).apply(f)
  else -> ProblemDescriptorWithUserData(this, UserDataHolderBase()).apply(f)
}
