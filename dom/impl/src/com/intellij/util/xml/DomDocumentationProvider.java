package com.intellij.util.xml;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;

/**
 * @author Dmitry Avdeev
 */
public class DomDocumentationProvider implements DocumentationProvider {

  public String getQuickNavigateInfo(final PsiElement element) {
    return null;
  }

  public String getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return null;
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    final DomElement domElement = DomUtil.getDomElement(element);
    return domElement == null ? null : ElementPresentationManagerImpl.getDocumentationForElement(domElement);
  }

  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return null;
  }
}
