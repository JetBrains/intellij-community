// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.presentation.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.ui.NewUiValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public final class ClassPresentationProvider implements ItemPresentationProvider<PsiClass> {
  @Override
  public ItemPresentation getPresentation(final @NotNull PsiClass psiClass) {
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
          LanguageLevel version = JavaMultiReleaseUtil.getVersion(file);
          if (version != null) {
            packageName += "/" + JavaPsiBundle.message("class.file.version", version.feature());
          }
          return NewUiValue.isEnabled() && !ApplicationManager.getApplication().isUnitTestMode()
                 ? JavaPsiBundle.message("aux.context.display", packageName) : "(" + packageName + ")";
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
        return ReadAction.compute(() -> psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS));
      }
    };
  }
}
