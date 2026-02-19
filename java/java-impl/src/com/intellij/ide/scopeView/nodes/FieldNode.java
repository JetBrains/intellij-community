// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.scopeView.nodes;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

public class FieldNode extends MemberNode<PsiField> {

  public FieldNode(final PsiField field) {
    super(field);
  }

  @Override
  public String toString() {
    final PsiField field = (PsiField)getPsiElement();
    if (field == null || !field.isValid()) return "";
    String name = PsiFormatUtil.formatVariable(
      field,
      PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_INITIALIZER,
      PsiSubstitutor.EMPTY);
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
