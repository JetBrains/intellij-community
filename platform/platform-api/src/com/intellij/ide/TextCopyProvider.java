// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.Collection;

/**
 * Base class for text-based copy provider
 *
 * @author Konstantin Bulenkov
 */
public abstract class TextCopyProvider implements CopyProvider {
  /**
   * Returns a collection of text blocks to be joined using {@link #getLinesSeparator()} separator.
   * Returns null or empty collection if copy operation can not be performed
   *
   * @return collection of text blocks or null
   */
  public abstract @Nullable Collection<String> getTextLinesToCopy();

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    Collection<String> lines = getTextLinesToCopy();
    if (lines != null && !lines.isEmpty()) {
      String text = StringUtil.join(lines, getLinesSeparator());
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
  }

  public String getLinesSeparator() {
    return "\n";
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return getTextLinesToCopy() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }
}
