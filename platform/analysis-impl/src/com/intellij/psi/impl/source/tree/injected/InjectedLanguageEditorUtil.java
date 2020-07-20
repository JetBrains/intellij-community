// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public final class InjectedLanguageEditorUtil {

  @NotNull
  public static Editor getTopLevelEditor(@NotNull Editor editor) {
    return editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
  }
}
