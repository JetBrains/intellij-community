// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiPresentableMetaData
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.psi.search.SearchScope
import javax.swing.Icon

internal class HttpHeaderElement(private val parent: PsiElement,
                                 private val headerName: String) : FakePsiElement(), SyntheticElement, PsiMetaOwner, PsiPresentableMetaData {
  override fun getName(): String = headerName
  override fun getParent(): PsiElement = parent
  override fun getName(context: PsiElement): String = name
  override fun getDeclaration(): PsiElement = this
  override fun canNavigate(): Boolean = true

  override fun getUseScope(): SearchScope = allScope(project)
  override fun getResolveScope(): GlobalSearchScope = allScope(project)

  override fun init(element: PsiElement) {}

  override fun getIcon(): Icon = AllIcons.Nodes.Type
  override fun getMetaData(): PsiMetaData = this
  override fun getNavigationElement(): PsiElement = this
  override fun getTypeName(): String = MicroservicesBundle.message("http.header.element")

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return another is HttpHeaderElement && another.headerName == headerName
  }

  override fun navigate(requestFocus: Boolean) {
    if (DumbService.Companion.getInstance(project).isDumb) return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
    ShowUsagesAction.startFindUsages(this, popupPosition, editor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HttpHeaderElement

    return headerName == other.headerName
  }

  override fun hashCode(): Int {
    return headerName.hashCode()
  }
}