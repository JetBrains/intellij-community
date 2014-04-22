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
package com.intellij.openapi.editor.richcopy;

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Transferable;

public abstract class BaseTextWithMarkupCopyPasteProcessor<T extends TextBlockTransferableData> implements CopyPastePostProcessor<T>, TextWithMarkupBuilder {
  private static final Logger LOG = Logger.getInstance("#" + BaseTextWithMarkupCopyPasteProcessor.class.getName());
  private static final String TRUNCATED_MESSAGE = "... truncated ...";

  private T myData;
  private int mySizeLimit;
  protected StringBuilder myBuilder;
  protected String myDefaultFontFamily;
  protected Color myDefaultForeground;
  protected Color myDefaultBackground;
  protected int myFontSize;

  protected BaseTextWithMarkupCopyPasteProcessor(TextWithMarkupProcessor processor) {
    processor.addBuilder(this);
  }

  @Nullable
  @Override
  public T collectTransferableData(PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
    if (!Registry.is("editor.richcopy.enable")) {
      return null;
    }
    releaseBuilder(); // to avoid leakage in case 'complete' method wasn't called due to some exception
    T data = myData;
    myData = null;
    return data;
  }

  @Nullable
  @Override
  public T extractTransferableData(Transferable content) {
    return null;
  }

  @Override
  public void processTransferableData(Project project,
                                      Editor editor,
                                      RangeMarker bounds,
                                      int caretOffset,
                                      Ref<Boolean> indented,
                                      T value) {

  }

  @Override
  public void init(Color defaultForeground, Color defaultBackground, String defaultFontFamily, int fontSize) {
    myData = null;
    myDefaultForeground = defaultForeground;
    myDefaultBackground = defaultBackground;
    myDefaultFontFamily = defaultFontFamily;
    myFontSize = fontSize;
    allocateBuilder();
    mySizeLimit = Registry.intValue("editor.richcopy.max.size.megabytes") * 1048576;
    doInit();
  }

  @Override
  public boolean isOverflowed() {
    boolean overflowed = myBuilder.length() > mySizeLimit;
    if (overflowed) {
      setFontFamily(myDefaultFontFamily);
      setForeground(myDefaultForeground);
      setBackground(myDefaultBackground);
      addTextFragment(TRUNCATED_MESSAGE, 0, TRUNCATED_MESSAGE.length());
    }
    return overflowed;
  }

  @Override
  public void complete() {
    try {
      doComplete();
      String data = myBuilder.toString();
      if (Registry.is("editor.richcopy.debug")) {
        LOG.info("Resulting text: \n" + data);
      }
      myData = createTransferable(data);
    }
    finally {
      releaseBuilder();
    }
  }

  protected abstract T createTransferable(@NotNull String data);
  protected abstract void doInit();
  protected abstract void doComplete();

  private void allocateBuilder() {
    if (myBuilder == null) {
      myBuilder = StringBuilderSpinAllocator.alloc();
    }
  }

  private void releaseBuilder() {
    if (myBuilder != null) {
      StringBuilderSpinAllocator.dispose(myBuilder);
      myBuilder = null;
    }
  }
}
