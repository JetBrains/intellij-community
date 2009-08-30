package com.intellij.refactoring.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

import javax.swing.*;
import java.awt.*;

/**
 *  @author dsl
 */
public class MethodCellRenderer extends DefaultListCellRenderer {
  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    PsiMethod method = (PsiMethod) value;

    final String text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
              PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
              PsiFormatUtil.SHOW_TYPE);
    setText(text);

    final int flags = Iconable.ICON_FLAG_VISIBILITY;

    Icon icon = method.getIcon(flags);
    if(icon != null) setIcon(icon);
    return this;
  }

}
