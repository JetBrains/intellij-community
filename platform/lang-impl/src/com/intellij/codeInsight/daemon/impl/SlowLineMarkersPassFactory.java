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

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class SlowLineMarkersPassFactory extends AbstractProjectComponent implements DirtyScopeTrackingHighlightingPassFactory {
  public SlowLineMarkersPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, Pass.UPDATE_OVERRIDEN_MARKERS);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "SlowLineMarkersPassFactory";
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return null;
    return new SlowLineMarkersPass(myProject, file, editor, textRange);
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_OVERRIDEN_MARKERS);
  }

  @Override
  public int getPassId() {
    return Pass.UPDATE_OVERRIDEN_MARKERS;
  }
}
