// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

public final class TodoAttributesUtil {
  public static @NotNull TodoAttributes createDefault() {
    return new TodoAttributes(getDefaultColorSchemeTextAttributes());
  }

  public static @NotNull TextAttributes getDefaultColorSchemeTextAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.TODO_DEFAULT_ATTRIBUTES).clone();
  }
}
