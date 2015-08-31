/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
   * Returns null of empty collection if copy operation can not be performed
   *
   * @return collection of text blocks or null
   */
  @Nullable
  public abstract Collection<String> getTextLinesToCopy();

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
