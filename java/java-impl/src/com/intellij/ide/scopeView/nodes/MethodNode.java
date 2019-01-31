// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.scopeView.nodes;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

public class MethodNode extends MemberNode<PsiMethod> {

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

  @Override
  public int getWeight() {
    return 5;
  }
}
