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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 13:47:11
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a list cell which contains a class
 */
public class ClassCellRenderer extends DefaultListCellRenderer {
  private final boolean myShowReadOnly;
  public ClassCellRenderer() {
    setOpaque(true);
    myShowReadOnly = true;
  }

  public ClassCellRenderer(boolean showReadOnly) {
    setOpaque(true);
    myShowReadOnly = showReadOnly;
  }

  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (value != null) {
      return customizeRenderer(this, value, myShowReadOnly);
    }
    return rendererComponent;
  }

  public static JLabel customizeRenderer(final JLabel cellRendererComponent, @NotNull final Object value, final boolean showReadOnly) {
    PsiClass aClass = (PsiClass) value;
    cellRendererComponent.setText(getClassText(aClass));

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (showReadOnly) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    Icon icon = aClass.getIcon(flags);
    if(icon != null) {
      cellRendererComponent.setIcon(icon);
    }
    return cellRendererComponent;
  }

  private static String getClassText(@NotNull PsiClass aClass) {
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName != null) {
      return qualifiedName;
    }

    String name = aClass.getName();
    if (name != null) {
      return name;
    }

    return RefactoringBundle.message("anonymous.class.text");
  }
}
