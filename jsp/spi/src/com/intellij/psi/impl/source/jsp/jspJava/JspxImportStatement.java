/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.PsiImportStatement;

/**
 * @author peter
 */
public interface JspxImportStatement extends PsiImportStatement {
  boolean isForeignFileImport();
}
