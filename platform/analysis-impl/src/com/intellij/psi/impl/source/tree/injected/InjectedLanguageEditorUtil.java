// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public final class InjectedLanguageEditorUtil {

  public static @NotNull Editor getTopLevelEditor(@NotNull Editor editor) {
    return editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
  }
}
