// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.ide.CopyPasteManager;

import java.awt.datatransfer.DataFlavor;


public class ClipboardMacro extends SimpleMacro {
  public ClipboardMacro() {
    super("clipboard");
  }

  @Override
  protected String evaluateSimpleMacro(Expression[] params, ExpressionContext context) {
    String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return text != null ? text : "";
  }
}
