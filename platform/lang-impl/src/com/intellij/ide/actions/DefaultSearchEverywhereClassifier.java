// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DefaultSearchEverywhereClassifier implements SearchEverywhereClassifier {
  @Override
  public boolean isClass(@Nullable Object o) {
    return o instanceof PsiElement;
  }

  @Override
  public boolean isSymbol(@Nullable Object o) {
    if (o instanceof PsiElement e) {
      return !e.getLanguage().is(Language.findLanguageByID("JAVA")) || !(e.getParent() instanceof PsiFile);
    }
    return false;
  }

  @Override
  public @Nullable VirtualFile getVirtualFile(@NotNull Object o) {
    if (o instanceof PsiElement element) {
      final PsiFile file = element.getContainingFile();
      return file != null ? file.getVirtualFile() : null;
    }
    return null;
  }

  @Override
  public @Nullable Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    return null;
  }
}
