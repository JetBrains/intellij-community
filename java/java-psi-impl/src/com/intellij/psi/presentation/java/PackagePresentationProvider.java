// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PackagePresentationProvider implements ItemPresentationProvider<PsiPackage> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiPackage aPackage) {
    return new ColoredItemPresentation() {
      @Override
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      @Override
      public String getPresentableText() {
        return aPackage.getName();
      }

      @Override
      public String getLocationString() {
        return aPackage.getQualifiedName();
      }

      @Override
      public Icon getIcon(boolean open) {
        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
      }
    };
  }
}
