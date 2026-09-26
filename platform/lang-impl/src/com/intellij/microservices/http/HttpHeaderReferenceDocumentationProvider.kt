package com.intellij.microservices.http

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.Nls

internal class HttpHeaderReferenceDocumentationProvider : DocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    return getDocumentation(element)?.generateDoc()
  }

  private fun getDocumentation(element: PsiElement): HttpHeaderDocumentation? {
    if (element is HttpHeaderElement) {
      val name: String = element.name
      if (StringUtil.isNotEmpty(name)) {
        return HttpHeadersDictionary.getDocumentation(name)
      }
    }
    return null
  }

  override fun getDocumentationElementForLookupItem(psiManager: PsiManager?, item: Any, element: PsiElement?): PsiElement? {
    if (element != null && item is HttpHeaderDocumentation) {
      return HttpHeaderElement(element, item.name)
    }
    return null
  }
}