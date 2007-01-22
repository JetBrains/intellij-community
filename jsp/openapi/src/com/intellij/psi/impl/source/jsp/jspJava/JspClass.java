/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.PsiClass;
import com.intellij.psi.jsp.JspFile;

/**
 * @author peter
 */
public interface JspClass extends PsiClass {
  JspHolderMethod getHolderMethod();

  JspFile getJspxFile();
}
