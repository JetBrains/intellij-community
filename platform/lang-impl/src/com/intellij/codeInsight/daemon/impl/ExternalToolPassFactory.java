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
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
*/
public class ExternalToolPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ExternalToolPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    // start after PostHighlightingPass completion since it could report errors that can prevent us to run
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.POST_UPDATE_ALL}, null, true, Pass.EXTERNAL_TOOLS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExternalToolPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.EXTERNAL_TOOLS);
    if (textRange == null || !externalAnnotatorsDefined(file)) {
      return null;
    }
    return new ExternalToolPass(file, editor, textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static boolean externalAnnotatorsDefined(PsiFile file) {
    for (Language language : file.getViewProvider().getLanguages()) {
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, file);
      if (!externalAnnotators.isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
