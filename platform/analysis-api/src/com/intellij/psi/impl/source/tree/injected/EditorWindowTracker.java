// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  /**
   * @param hostEditor host editor
   * @param injectedFile injected file, its host must be in host editor
   * @return editor that corresponds to the injected file (may create a new one)
   */
  @NotNull
  public abstract Editor getEditorForInjectedFile(@NotNull Editor hostEditor,
                                                  @NotNull PsiFile injectedFile);

  public abstract void disposeInvalidEditors();

  public abstract void disposeEditorFor(@NotNull DocumentWindow documentWindow);
}
