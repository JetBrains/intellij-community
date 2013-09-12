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

import com.intellij.codeHighlighting.MainHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class PostHighlightingPassFactory extends AbstractProjectComponent implements MainHighlightingPassFactory {
  private static final Key<Long> LAST_POST_PASS_TIMESTAMP = Key.create("LAST_POST_PASS_TIMESTAMP");
  public PostHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL,}, null, true, Pass.POST_UPDATE_ALL);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "PostHighlightingPassFactory";
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (textRange == null) {
      Long lastStamp = file.getUserData(LAST_POST_PASS_TIMESTAMP);
      long currentStamp = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
      if (lastStamp != null && lastStamp == currentStamp || !ProblemHighlightFilter.shouldHighlightFile(file)) {
        return null;
      }
    }

    return new PostHighlightingPass(myProject, file, editor, editor.getDocument(), new DefaultHighlightInfoProcessor());
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    return new PostHighlightingPass(myProject, file, null, document, highlightInfoProcessor);
  }

  public static void markFileUpToDate(@NotNull PsiFile file) {
    long lastStamp = PsiModificationTracker.SERVICE.getInstance(file.getProject()).getModificationCount();
    file.putUserData(LAST_POST_PASS_TIMESTAMP, lastStamp);
  }
}
