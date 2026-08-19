// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocInfoHtmlPrinter;
import com.intellij.codeInsight.javadoc.JavaDocInfoPrinter;
import com.intellij.lang.java.JavaDocumentationProvider;import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/// HTML variant of the javadoc info generator
public final class JavaDocInfoGeneratorHtmlTest extends JavaDocInfoGeneratorTest {
  @Override
  protected @NotNull JavaDocInfoPrinter getPrinter() {
    return new JavaDocInfoHtmlPrinter();
  }
  
  @Override
  protected @NotNull String getExpectedFileExtension() {
    return ".html";
  }
  
  @Override
  protected @NotNull String decorate(@NotNull String text) {
    return com.intellij.codeInsight.documentation.DocumentationManager.decorate(text, null, null);
  }

  @Override
  protected String replaceEnvironmentDependentContent(String html) {
    return html != null 
    ? StringUtil.convertLineSeparators(html.trim()).replaceAll("<base href=\"[^\"]*\">", "<base href=\"placeholder\">")
    .replaceAll("[ \t]+\\n", "\n") 
    : null;
  }

  public void testClassTypeParamsPresentation() {
    PsiClass psiClass = getTestClass();
    PsiReferenceList extendsList = psiClass.getExtendsList();
    assertNotNull(extendsList);
    PsiJavaCodeReferenceElement referenceElement = extendsList.getReferenceElements()[0];
    PsiClass superClass = extendsList.getReferencedTypes()[0].resolve();
    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(superClass, referenceElement);
    assertNotNull(docInfo);
    assertFileTextEquals(UIUtil.getHtmlBodyWithoutPreWrapper(docInfo));
  }
}
