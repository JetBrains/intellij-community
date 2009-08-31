/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.scopeView.nodes;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class FieldNode extends BasePsiNode<PsiField> {

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

  public int getWeight() {
    return 5;
  }

  @Override
  public boolean isDeprecated() {
    final PsiField psiField = (PsiField)getPsiElement();
    return psiField != null && psiField.isDeprecated();
  }
}
