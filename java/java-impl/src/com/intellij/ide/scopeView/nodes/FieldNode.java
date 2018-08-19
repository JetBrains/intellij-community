// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.scopeView.nodes;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

public class FieldNode extends MemberNode<PsiField> {

  public FieldNode(final PsiField field) {
    super(field);
  }

  public String toString() {
    final PsiField field = (PsiField)getPsiElement();
    if (field == null || !field.isValid()) return "";
    String name = PsiFormatUtil.formatVariable(
      field,
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_INITIALIZER,
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
