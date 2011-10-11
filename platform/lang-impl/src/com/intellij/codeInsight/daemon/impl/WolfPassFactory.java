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
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class WolfPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private long myPsiModificationCount;

  public WolfPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, new int[]{Pass.LOCAL_INSPECTIONS}, false, Pass.WOLF);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "WolfPassFactory";
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    final long psiModificationCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    if (psiModificationCount == myPsiModificationCount) {
      return null; //optimization
    }
    return new WolfHighlightingPass(myProject, editor.getDocument(), file){
      @Override
      protected void applyInformationWithProgress() {
        super.applyInformationWithProgress();
        myPsiModificationCount = psiModificationCount;
      }
    };
  }
}
