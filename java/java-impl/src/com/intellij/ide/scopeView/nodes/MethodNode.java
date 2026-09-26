// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.scopeView.nodes;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

public class MethodNode extends MemberNode<PsiMethod> {

  public MethodNode(final PsiMethod element) {
    super(element);
  }

  @Override
  public String toString() {
    final PsiMethod method = (PsiMethod)getPsiElement();
    if (method == null || !method.isValid()) return "";
    if (DumbService.isDumb(myProject)) return method.getName();

    String name = PsiFormatUtil.formatMethod(
      method,
      PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER |
                            PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE
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
