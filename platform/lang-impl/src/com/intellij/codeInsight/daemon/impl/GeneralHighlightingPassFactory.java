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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
public class GeneralHighlightingPassFactory extends AbstractProjectComponent implements MainHighlightingPassFactory {
  public GeneralHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar, DefaultHighlightVisitor dhi) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this,
                                                                 null,
                                                                 new int[]{Pass.UPDATE_FOLDING}, false, Pass.UPDATE_ALL);
    assert dhi != null;
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "GeneralHighlightingPassFactory";
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, editor.getDocument());
    ProperTextRange visibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(editor);
    return new GeneralHighlightingPass(myProject, file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true, visibleRange, editor, new DefaultHighlightInfoProcessor());
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    // no applying to the editor - for read-only analysis only
    return new GeneralHighlightingPass(myProject, file, document, 0, file.getTextLength(),
                                       true, new ProperTextRange(0, document.getTextLength()), null, highlightInfoProcessor);
  }
}
