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
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.view.RawTextHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public abstract class BaseTextWithMarkupCopyPasteProcessor<T extends RawTextHolder & TextBlockTransferableData>
  implements CopyPastePostProcessor<T>, TextWithMarkupBuilder {
  private T myData;

  protected BaseTextWithMarkupCopyPasteProcessor(TextWithMarkupProcessor processor) {
    processor.addBuilder(this);
  }

  @Nullable
  @Override
  public T collectTransferableData(PsiFile file, Editor editor, int[] startOffsets, int[] endOffsets) {
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
  public void build(SyntaxInfo syntaxInfo) {
    myData = doBuild(syntaxInfo);
  }

  @Override
  public void reset() {
    myData = null;
  }

  protected abstract T doBuild(SyntaxInfo info);

  public static class RawTextSetter implements CopyPastePreProcessor {
    private final BaseTextWithMarkupCopyPasteProcessor myProcessor;

    public RawTextSetter(BaseTextWithMarkupCopyPasteProcessor processor) {
      myProcessor = processor;
    }

    @Override
    @Nullable
    public String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, String text) {
      if (myProcessor.myData != null) {
        myProcessor.myData.setRawText(text);
        myProcessor.myData = null;
      }
      return null; // noop
    }

    @Override
    public String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
      return text; // noop
    }
  }

}
