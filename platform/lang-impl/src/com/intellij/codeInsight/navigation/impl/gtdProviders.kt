// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.*
import com.intellij.codeInsight.navigation.BaseCtrlMouseInfo.getReferenceRanges
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal fun fromGTDProviders(project: Project, editor: Editor, offset: Int): GTDActionData? {
  return processInjectionThenHost(editor, offset) { _editor, _offset ->
    fromGTDProvidersInner(project, _editor, _offset)
  }
}

/**
 * @see com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findTargetElementsFromProviders
 */
private fun fromGTDProvidersInner(project: Project, editor: Editor, offset: Int): GTDActionData? {
  val document = editor.document
  val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
  val adjustedOffset: Int = TargetElementUtil.adjustOffset(file, document, offset)
  val leafElement: PsiElement = file.findElementAt(adjustedOffset) ?: return null
  for (handler in GotoDeclarationHandler.EP_NAME.extensionList) {
    val fromProvider: Array<out PsiElement>? = handler.getGotoDeclarationTargets(leafElement, offset, editor)
    if (fromProvider.isNullOrEmpty()) {
      continue
    }
    return GTDProviderData(leafElement, fromProvider.toList(), handler)
  }
  return null
}

private class GTDProviderData(
  private val leafElement: PsiElement,
  private val targetElements: Collection<PsiElement>,
  private val navigationProvider: GotoDeclarationHandler
) : GTDActionData {

  init {
    require(targetElements.isNotEmpty())
  }

  override fun ctrlMouseInfo(): CtrlMouseInfo {
    val singleTarget = targetElements.singleOrNull()
    return if (singleTarget == null) {
      MultipleTargetElementsInfo(leafElement)
    }
    else {
      SingleTargetElementInfo(leafElement, singleTarget)
    }
  }

  override fun ctrlMouseData(): CtrlMouseData {
    val singleTarget = targetElements.singleOrNull()
    if (singleTarget == null) {
      return multipleTargetsCtrlMouseData(getReferenceRanges(leafElement))
    }
    else {
      return psiCtrlMouseData(leafElement, singleTarget)
    }
  }

  override fun result(): NavigationActionResult? {
    return when (targetElements.size) {
      0 -> null
      1 -> {
        targetElements.single().gtdTargetNavigatable()?.navigationRequest()?.let { request ->
          SingleTarget(request, navigationProvider)
        }
      }
      else -> {
        val targets = targetElements.map { targetElement ->
          LazyTargetWithPresentation(
            { targetElement.psiNavigatable()?.navigationRequest() },
            targetPresentation(targetElement),
            navigationProvider
          )
        }
        NavigationActionResult.MultipleTargets(targets)
      }
    }
  }
}
