package com.intellij.lang.java;

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;

/**
 * @author spleaner
 */
public class FileDocumentationProvider extends QuickDocumentationProvider {

  public String getQuickNavigateInfo(PsiElement element) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final JavaDocInfoGenerator javaDocInfoGenerator = new JavaDocInfoGenerator(element.getProject(), element);
    return JavaDocExternalFilter.filterInternalDocInfo(javaDocInfoGenerator.generateFileInfo());
  }
}
