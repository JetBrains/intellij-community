/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.jsp;

import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.psi.*;
import com.intellij.psi.jsp.tagLibrary.JspTagLibraryInfo;

public interface JspFile extends PsiFile, JspElement {
  JspDirective[] getTaglibDirectives(); // TODO[ik]: change this method to return something more generic

  WebModuleProperties getWebModuleProperties();
  WebDirectoryElement getParentWebDirectory();
  String getWebPath();

  PsiElement[] getContentsElements();
}
