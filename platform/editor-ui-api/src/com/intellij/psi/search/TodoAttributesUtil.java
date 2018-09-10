// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

public class TodoAttributesUtil {
  @NotNull
  public static TodoAttributes createDefault() {
    return new TodoAttributes(getDefaultColorSchemeTextAttributes());
  }

  @NotNull
  public static TextAttributes getDefaultColorSchemeTextAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.TODO_DEFAULT_ATTRIBUTES).clone();
  }
}
