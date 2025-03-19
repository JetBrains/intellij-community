// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiPresentableMetaData
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import javax.swing.Icon

internal class HttpMethodElement(private val parent: PsiElement,
                                 private val methodName: String,
                                 private val refTextRange: TextRange
) : FakePsiElement(), SyntheticElement, PsiMetaOwner, PsiPresentableMetaData {
  override fun getName(): String = methodName

  override fun getName(context: PsiElement): String = name

  override fun getParent(): PsiElement = parent

  override fun getDeclaration(): PsiElement = this

  override fun getMetaData(): PsiMetaData = this

  override fun getNavigationElement(): PsiElement = this

  override fun canNavigate(): Boolean = true

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun getTypeName(): String = MicroservicesBundle.message("http.method.element")

  override fun getUseScope(): SearchScope {
    return GlobalSearchScope.allScope(project)
  }

  override fun getResolveScope(): GlobalSearchScope {
    return GlobalSearchScope.allScope(project)
  }

  override fun init(element: PsiElement) {}

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return another is HttpMethodElement && another.methodName == methodName
  }

  override fun getTextOffset(): Int {
    return getParent().textOffset + refTextRange.startOffset
  }

  override fun navigate(requestFocus: Boolean) {
    if (DumbService.Companion.getInstance(project).isDumb) return
    val file = containingFile
    if (file != null && file.isValid) {
      val descriptor = PsiNavigationSupport.getInstance().createNavigatable(project, file.virtualFile, textOffset)
      descriptor.navigate(requestFocus)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HttpMethodElement

    return methodName == other.methodName
  }

  override fun hashCode(): Int {
    return methodName.hashCode()
  }
}