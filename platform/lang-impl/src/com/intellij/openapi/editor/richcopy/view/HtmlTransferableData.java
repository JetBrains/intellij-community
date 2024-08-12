// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;

public final class HtmlTransferableData extends HtmlSyntaxInfoReader implements RawTextWithMarkup {
  public static final @NotNull DataFlavor FLAVOR = new DataFlavor("text/html; class=java.io.Reader; charset=UTF-8", "HTML text");
  public static final int PRIORITY = 200;

  public HtmlTransferableData(@NotNull SyntaxInfo syntaxInfo, int tabSize) {
    super(syntaxInfo, tabSize);
  }

  @Override
  public @Nullable DataFlavor getFlavor() {
    return FLAVOR;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
