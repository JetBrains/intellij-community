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

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author yole
 */
public class IdentifierHighlighterPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private static final int[] AFTER_PASSES = {Pass.UPDATE_ALL};

  private static boolean ourTestingIdentifierHighlighting = false;
  
  public IdentifierHighlighterPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, AFTER_PASSES, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    if (!editor.isOneLineMode() &&
        CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET &&
        !DumbService.isDumb(myProject) &&
        (!ApplicationManager.getApplication().isHeadlessEnvironment() || ourTestingIdentifierHighlighting) &&
        (file.isPhysical() || file.getOriginalFile().isPhysical())) {
      return new IdentifierHighlighterPass(file.getProject(), file, editor);
    }

    return null;
  }

  @TestOnly
  public static void doWithHighlightingEnabled(@NotNull Runnable r) {
    ourTestingIdentifierHighlighting = true;
    try {
      r.run();
    }
    finally {
      ourTestingIdentifierHighlighting = false;
    }
  }
}
