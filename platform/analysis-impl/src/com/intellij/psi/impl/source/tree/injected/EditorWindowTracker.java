// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class EditorWindowTracker {
  public static EditorWindowTracker getInstance() {
    return ApplicationManager.getApplication().getService(EditorWindowTracker.class);
  }

  public abstract void disposeInvalidEditors();

  public abstract void disposeEditorFor(@NotNull DocumentWindow documentWindow);
}
