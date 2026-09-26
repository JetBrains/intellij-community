// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class TemplateLineStartHandler extends TemplateLineStartEndHandler {
  public TemplateLineStartHandler(final EditorActionHandler originalHandler) {
    super(originalHandler, true, false);
  }
}
