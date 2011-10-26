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
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SimpleMethodCallLookupElement extends LookupElement {
  private final PsiMethod myMethod;

  public SimpleMethodCallLookupElement(final PsiMethod method) {
    myMethod = method;
  }

  @Override
  @NotNull
  public String getLookupString() {
    return myMethod.getName();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    new MethodParenthesesHandler(myMethod, true).handleInsert(context, this);
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(myMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    presentation.setItemText(myMethod.getName());
    presentation.setTailText(PsiFormatUtil.formatMethod(myMethod,
                                                        PsiSubstitutor.EMPTY,
                                                        PsiFormatUtil.SHOW_PARAMETERS,
                                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE));
    final PsiType returnType = myMethod.getReturnType();
    if (returnType != null) {
      presentation.setTypeText(returnType.getCanonicalText());
    }
  }

}
