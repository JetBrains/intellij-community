// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithoutContent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UIComponentVirtualFile extends LightVirtualFile implements VirtualFileWithoutContent {
  private final Content myUi;

  public UIComponentVirtualFile(@NotNull String name, Content ui) {
    super(name);
    myUi = ui;
    putUserData(FileEditorManagerImpl.FORBID_PREVIEW_TAB, true);
  }

  public Content getUi() {
    return myUi;
  }

  public interface Content {
    @NotNull
    JComponent createComponent();

    @Nullable JComponent getPreferredFocusedComponent();

    default @Nullable Icon getIcon() {
      return null;
    }
  }

  static class UIComponentVirtualFileIconProvider extends IconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
      if (element instanceof PsiFile) {
        VirtualFile file = ((PsiFile)element).getVirtualFile();
        if (file instanceof UIComponentVirtualFile) {
          return ((UIComponentVirtualFile)file).myUi.getIcon();
        }
      }
      return null;
    }
  }
}
