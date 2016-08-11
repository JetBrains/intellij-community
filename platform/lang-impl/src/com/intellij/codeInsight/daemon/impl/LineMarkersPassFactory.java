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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.MarkupModelImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class LineMarkersPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public LineMarkersPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, Pass.LINE_MARKERS);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "LineMarkersPassFactory";
  }
  
  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange restrictRange = calculateRangeToProcessForSyntaxPass(editor);
    Document document = editor.getDocument();
    if (restrictRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, document);
    ProperTextRange visibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(editor);
    return new LineMarkersPass(myProject, file, document, expandRangeToCoverWholeLines(document, visibleRange), expandRangeToCoverWholeLines(document, restrictRange));
  }

  @Nullable
  private static TextRange calculateRangeToProcessForSyntaxPass(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
  }

  static TextRange expandRangeToCoverWholeLines(@NotNull Document document, TextRange textRange) {
    if (textRange == null) return null;
    return MarkupModelImpl.roundToLineBoundaries(document, textRange.getStartOffset(), textRange.getEndOffset());
  }
}