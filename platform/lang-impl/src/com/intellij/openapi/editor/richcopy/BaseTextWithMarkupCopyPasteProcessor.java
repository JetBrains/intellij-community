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
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public abstract class BaseTextWithMarkupCopyPasteProcessor<T extends TextBlockTransferableData> implements CopyPastePostProcessor<T>, TextWithMarkupBuilder {
  private static final Logger LOG = Logger.getInstance("#" + BaseTextWithMarkupCopyPasteProcessor.class.getName());

  private T myData;

  protected BaseTextWithMarkupCopyPasteProcessor(TextWithMarkupProcessor processor) {
    processor.addBuilder(this);
  }

  @Nullable
  @Override
  public T collectTransferableData(@Nullable PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
    if (!Registry.is("editor.richcopy.enable")) {
      return null;
    }
    return myData;
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
  public void build(CharSequence charSequence, SyntaxInfo syntaxInfo) {
    String stringRepresentation = null;
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      buildStringRepresentation(buffer, charSequence, syntaxInfo, Registry.intValue("editor.richcopy.max.size.megabytes") * 1048576);
      stringRepresentation = buffer.toString();
      if (Registry.is("editor.richcopy.debug")) {
        LOG.info("Resulting text: \n'" + stringRepresentation + "'");
      }
    }
    catch (Exception e){
      LOG.error(e);
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
    myData = stringRepresentation == null ? null : createTransferable(stringRepresentation);
  }

  @Override
  public void reset() {
    myData = null;
  }

  protected abstract void buildStringRepresentation(@NotNull StringBuilder buffer, @NotNull CharSequence rawText, @NotNull SyntaxInfo syntaxInfo, int maxLength);
  protected abstract T createTransferable(@NotNull String data);
}
