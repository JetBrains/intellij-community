/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
public class LocalInspectionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public LocalInspectionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL/*, Pass.POPUP_HINTS*/}, true, Pass.LOCAL_INSPECTIONS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "LocalInspectionsPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(myProject, editor.getDocument(), LocalInspectionsPass.IN_PROGRESS_ICON,
                                                                             LocalInspectionsPass.PRESENTABLE_NAME);
    TextRange visibleRange = VisibleHighlightingPassFactory.calculateVisibleRange(editor);
    return new LocalInspectionsPass(file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), visibleRange){
      List<LocalInspectionTool> getInspectionTools(InspectionProfileWrapper profile) {
        List<LocalInspectionTool> tools = super.getInspectionTools(profile);
        List<LocalInspectionTool> result = new ArrayList<LocalInspectionTool>(tools.size());
        for (LocalInspectionTool tool : tools) {
          if (!tool.runForWholeFile()) result.add(tool);
        }
        return result;
      }
    };
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    return FileStatusMap.getDirtyTextRange(editor, Pass.LOCAL_INSPECTIONS);
  }
}
