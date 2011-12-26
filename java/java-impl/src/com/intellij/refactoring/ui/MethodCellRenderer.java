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

    Icon icon = method.getIcon(Iconable.ICON_FLAG_VISIBILITY);
    if(icon != null) setIcon(icon);
    return this;
  }

}
