/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  @Nls
  private static String getClassText(@NotNull PsiClass aClass) {
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
