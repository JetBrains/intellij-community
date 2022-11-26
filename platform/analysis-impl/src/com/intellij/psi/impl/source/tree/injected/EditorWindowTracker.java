// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class EditorWindowTracker {
  public static EditorWindowTracker getInstance() {
    return ApplicationManager.getApplication().getService(EditorWindowTracker.class);
  }

  @NotNull
  public abstract Editor createEditor(@NotNull DocumentWindow documentRange, 
                                      @NotNull Editor editor, 
                                      @NotNull PsiFile injectedFile);

  public abstract void disposeInvalidEditors();

  public abstract void disposeEditorFor(@NotNull DocumentWindow documentWindow);
}
