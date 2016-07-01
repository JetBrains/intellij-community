/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.RainbowProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RainbowIdentifierHighlighterPass extends TextEditorHighlightingPass {
  protected final PsiFile myFile;
  protected List<HighlightInfo> myHighlight;
  protected final Color myBackgroundColor;

  protected RainbowIdentifierHighlighterPass(@NotNull PsiFile file, @NotNull Editor editor) {
    super(file.getProject(), editor.getDocument(), false);
    myBackgroundColor = (editor instanceof EditorImpl) ? ((EditorImpl)editor).getBackgroundColor() : null;
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    final List<HighlightInfo> infos = new ArrayList<>();

    // myBackgroundColor takes into account "read only" editors
    final RainbowHighlighter rainbowHighlighter = new RainbowHighlighter(getColorsScheme(), myBackgroundColor);
    for (RainbowProvider processor : RainbowProvider.getRainbowFileProcessors()) {
      if (processor.isValidContext(myFile)) {
        infos.addAll(processor.getHighlights(myFile, rainbowHighlighter, progress));
      }
    }
    myHighlight = infos;
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myHighlight == null || myDocument == null) return;
    UpdateHighlightersUtil
      .setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myHighlight, getColorsScheme(), getId());
  }
}
