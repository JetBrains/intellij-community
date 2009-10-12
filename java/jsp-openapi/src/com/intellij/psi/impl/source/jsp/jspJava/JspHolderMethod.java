/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface JspHolderMethod extends PsiMethod, SyntheticElement {
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
