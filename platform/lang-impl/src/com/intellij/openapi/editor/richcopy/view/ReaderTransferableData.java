/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * @author Denis Zhdanov
 * @since 3/28/13 7:09 PM
 */
public class ReaderTransferableData extends Reader implements TextBlockTransferableData
{
  @NotNull
  private final DataFlavor myDataFlavor;
  @Nullable
  private Reader myDelegate;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  public ReaderTransferableData(@NotNull String data, @NotNull DataFlavor dataFlavor) {
    myDataFlavor = dataFlavor;
    myDelegate = new StringReader(data);
  }

  @Override
  public DataFlavor getFlavor() {
    return myDelegate == null ? null : myDataFlavor;
  }

  @Override
  public int getOffsetCount() {
    return 0;
  }

  @Override
  public int getOffsets(int[] offsets, int index) {
    return index;
  }

  @Override
  public int setOffsets(int[] offsets, int index) {
    return index;
  }

  @Override
  public int read() throws IOException {
    if (myDelegate == null) {
      return -1;
    }
    return myDelegate.read();
  }

  @Override
  public int read(@NotNull char[] cbuf, int off, int len) throws IOException {
    if (myDelegate == null) {
      return -1;
    }
    return myDelegate.read(cbuf, off, len);
  }

  @Override
  public void close() throws IOException {
    myDelegate = null;
  }
}
