// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Renders a list cell which contains a class.
 */
public class ClassCellRenderer extends ListCellRendererWrapper<PsiClass> {
  private final boolean myShowReadOnly;

  public ClassCellRenderer(ListCellRenderer original) {
    super();
    myShowReadOnly = true;
  }

  @Override
  public void customize(JList list, PsiClass aClass, int index, boolean selected, boolean hasFocus) {
    if (aClass != null) {
      setText(getClassText(aClass));

      int flags = Iconable.ICON_FLAG_VISIBILITY;
      if (myShowReadOnly) {
        flags |= Iconable.ICON_FLAG_READ_STATUS;
      }
      Icon icon = aClass.getIcon(flags);
      if (icon != null) {
        setIcon(icon);
      }
    }
  }

  private static @Nls String getClassText(@NotNull PsiClass aClass) {
    @NlsSafe String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    }

    @NlsSafe String name = aClass.getName();
    if (name != null) {
      return name;
    }

    return JavaRefactoringBundle.message("anonymous.class.text");
  }
}
