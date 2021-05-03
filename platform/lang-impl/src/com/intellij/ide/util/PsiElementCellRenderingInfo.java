// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.util.TextWithIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

public interface PsiElementCellRenderingInfo<T extends PsiElement> {
  default Icon getIcon(PsiElement element) {
    return element.getIcon(getIconFlags());
  }

  @Iconable.IconFlags
  int getIconFlags();

  String getElementText(T element);

  String getContainerText(T element, final String name);

  default Comparator<T> getComparator() {
    //noinspection unchecked,rawtypes
    return Comparator.comparing(element -> (Comparable)getComparingObject(element));
  }

  @NotNull
  default Comparable<?> getComparingObject(T element) {
    return ReadAction.compute(() -> {
      String elementText = getElementText(element);
      String containerText = getContainerText(element, elementText);
      TextWithIcon moduleTextWithIcon = PsiElementListCellRenderer.getModuleTextWithIcon(element);
      return (containerText == null ? elementText : elementText + " " + containerText) +
             (moduleTextWithIcon != null ? moduleTextWithIcon.getText() : "");
    });
  }
}
