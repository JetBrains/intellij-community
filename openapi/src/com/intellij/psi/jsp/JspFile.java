/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.jsp;

import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.lang.Language;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;

public interface JspFile extends PsiFile{
  WebModuleProperties getWebModuleProperties();
  WebDirectoryElement getParentWebDirectory();
  String getWebPath();

  PsiElement[] getContentsElements();

  boolean isErrorPage();
  boolean isSessionPage();

  XmlTag[] getDirectiveTags(JspDirectiveKind directiveKind);
  XmlTag createDirective(XmlTag context, JspDirectiveKind page);

  PsiClass getJavaRoot();

  PsiFile getBaseLanguageRoot();

  Language getBaseLanguage();
}
