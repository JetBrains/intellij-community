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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RainbowIdentifierHighlighterPass extends TextEditorHighlightingPass {
  protected final PsiFile myFile;
  protected final RainbowHighlighter myRainbowHighlighter;
  protected List<HighlightInfo> toHighlight;

  protected RainbowIdentifierHighlighterPass(@NotNull PsiFile file, @NotNull Editor editor) {
    super(file.getProject(), editor.getDocument(), false);
    myFile = file;
    myRainbowHighlighter = new RainbowHighlighter(editor.getColorsScheme());
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    // reference implementation!
    final List<HighlightInfo> infos = new ArrayList<>();
    myFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement e) {
        final HighlightInfo attrs;
        if (e instanceof PsiReference) {
          attrs = getInfo(e.getText(), e);
        }
        else if (e instanceof PsiNameIdentifierOwner) {
          PsiNameIdentifierOwner identifierOwner = (PsiNameIdentifierOwner)e;
          attrs = getInfo(identifierOwner.getName(), identifierOwner.getNameIdentifier());
        }
        else {
          attrs = null;
        }
        if (attrs != null) {
          infos.add(attrs);
        }
        super.visitElement(e);
      }
    });
    toHighlight = infos;
  }

  @Override
  public void doApplyInformationToEditor() {
    if (toHighlight == null || myDocument == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), toHighlight, getColorsScheme(), getId());
  }

  protected HighlightInfo getInfo(@Nullable String colorKey, @Nullable PsiElement id) {
    if (id == null || colorKey == null || StringUtil.isEmpty(colorKey)) return null;
    final TextAttributes attributes = myRainbowHighlighter.getAttributes(colorKey);
    return HighlightInfo
      .newHighlightInfo(RainbowHighlighter.RAINBOW_ELEMENT)
      .textAttributes(attributes)
      .range(id)
      .create();
  }
}
