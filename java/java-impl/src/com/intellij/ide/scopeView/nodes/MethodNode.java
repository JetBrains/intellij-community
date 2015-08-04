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

package com.intellij.ide.scopeView.nodes;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class MethodNode extends BasePsiNode<PsiMethod> {

  public MethodNode(final PsiMethod element) {
    super(element);
  }

  public String toString() {
    final PsiMethod method = (PsiMethod)getPsiElement();
    if (method == null || !method.isValid()) return "";
    if (DumbService.isDumb(myProject)) return method.getName();
    
    String name = PsiFormatUtil.formatMethod(
      method,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    return name;
  }

  public int getWeight() {
    return 5;
  }

  @Override
  public boolean isDeprecated() {
    final PsiMethod psiMethod = (PsiMethod)getPsiElement();
    return psiMethod != null && psiMethod.isDeprecated();
  }
}
