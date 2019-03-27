// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ParameterHintsPassFactory {
  protected static final Key<Long> PSI_MODIFICATION_STAMP = Key.create("psi.modification.stamp");

  private ParameterHintsPassFactory() {
  }

  public static long getCurrentModificationStamp(@NotNull PsiFile file) {
    return file.getManager().getModificationTracker().getModificationCount();
  }

  public static void forceHintsUpdateOnNextPass() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      forceHintsUpdateOnNextPass(editor);
    }
  }

  public static void forceHintsUpdateOnNextPass(@NotNull Editor editor) {
    editor.putUserData(PSI_MODIFICATION_STAMP, null);
  }

  protected static void putCurrentPsiModificationStamp(@NotNull Editor editor, @NotNull PsiFile file) {
    editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationStamp(file));
  }
}