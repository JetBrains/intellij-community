// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.intention

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.url.HTTP_SCHEME
import com.intellij.microservices.url.LOCALHOST
import com.intellij.microservices.url.references.UrlPathReference
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.psi.util.parents
import javax.swing.Icon

internal class OpenInWebBrowserIntention :
  PsiElementBaseIntentionAction(),
  Iconable,
  HighPriorityAction,
  MicroservicesIntentionPriorityComparableAction {

  override fun getText(): String = MicroservicesBundle.message("microservices.open.in.web.browser.intention.text")
  override fun getFamilyName(): String = MicroservicesBundle.message("microservices.open.in.web.browser.intention.family.name")
  override fun getIcon(flags: Int): Icon = AllIcons.RunConfigurations.Web_app

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val urlPathReference = findUrlPathReferences(element).find { it.isAtEnd } ?: return
    val request = urlPathReference.context.resolveRequests.firstOrNull() ?: return

    val scheme = request.schemeHint ?: HTTP_SCHEME
    val authority = request.authorityHint ?: LOCALHOST
    val path = request.path.getPresentation()

    BrowserUtil.browse("$scheme$authority$path")
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    return findUrlPathReferences(element).any()
  }

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = null

  override fun getFileModifierForPreview(target: PsiFile): FileModifier? = null

  private fun findUrlPathReferences(element: PsiElement): Sequence<UrlPathReference> {
    return element.parents(true)
      .take(3)
      .filter { WebReference.isWebReferenceWorthy(it) }
      .flatMap { it.references.asSequence() }
      .mapNotNull { PsiReferenceUtil.findReferenceOfClass(it, UrlPathReference::class.java) }
  }

  override val microservicesActionPriority: Int
    get() = 0
}