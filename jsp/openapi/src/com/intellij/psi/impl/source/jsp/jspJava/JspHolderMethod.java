/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface JspHolderMethod extends PsiMethod {
  @NonNls String JSP_CONTEXT_VAR_NAME = "jspContext";
  @NonNls String APPLICATION_VAR_NAME = "application";
  @NonNls String OUT_VAR_NAME = "out";
  @NonNls String CONFIG_VAR_NAME = "config";
  @NonNls String PAGE_VAR_NAME = "page";
  @NonNls String EXCEPTION_VAR_NAME = "exception";
  @NonNls String SESSION_VAR_NAME = "session";
  @NonNls String REQUEST_VAR_NAME = "request";
  @NonNls String RESPONSE_VAR_NAME = "response";

  JspClass getJspClass();
}
