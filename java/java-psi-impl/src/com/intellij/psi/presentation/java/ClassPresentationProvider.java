// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.presentation.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.ui.NewUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class ClassPresentationProvider implements ItemPresentationProvider<PsiClass> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiClass psiClass) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return ClassPresentationUtil.getNameForClass(psiClass, false);
      }

      @Override
      public String getLocationString() {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiClassOwner) {
          PsiClassOwner classOwner = (PsiClassOwner)file;
          String packageName = classOwner.getPackageName();
          if (packageName.isEmpty()) return null;
          return NewUi.isEnabled() ? JavaPsiBundle.message("aux.context.display", packageName) : "(" + packageName + ")";
        }
        return null;
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        try {
          if (psiClass.isDeprecated()) {
            return CodeInsightColors.DEPRECATED_ATTRIBUTES;
          }
        }
        catch (IndexNotReadyException ignore) {
        }
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
