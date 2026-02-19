// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.ApiStatus;

import java.awt.datatransfer.DataFlavor;


@ApiStatus.Internal
public final class ClipboardMacro extends SimpleMacro {
  public ClipboardMacro() {
    super("clipboard");
  }

  @Override
  protected String evaluateSimpleMacro(Expression[] params, ExpressionContext context) {
    String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return text != null ? text : "";
  }
}
