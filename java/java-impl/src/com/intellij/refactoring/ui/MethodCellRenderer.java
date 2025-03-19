// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;
import java.awt.*;

public class MethodCellRenderer extends DefaultListCellRenderer {
  @Override
  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    PsiMethod method = (PsiMethod) value;

    final String text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                   PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME |
                                                   PsiFormatUtilBase.SHOW_PARAMETERS,
                                                   PsiFormatUtilBase.SHOW_TYPE);
    setText(text);

    Icon icon = method.getIcon(Iconable.ICON_FLAG_VISIBILITY);
    if(icon != null) setIcon(icon);
    return this;
  }

}
