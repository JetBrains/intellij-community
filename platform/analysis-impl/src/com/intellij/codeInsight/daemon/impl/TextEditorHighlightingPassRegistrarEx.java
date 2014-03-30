/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 * Date: 21-Jun-2006
 */
public abstract class TextEditorHighlightingPassRegistrarEx extends TextEditorHighlightingPassRegistrar {

  public static TextEditorHighlightingPassRegistrarEx getInstanceEx(Project project) {
    return (TextEditorHighlightingPassRegistrarEx)getInstance(project);
  }

  @NotNull
  public abstract List<TextEditorHighlightingPass> instantiatePasses(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull int[] passesToIgnore);
  @NotNull
  public abstract List<TextEditorHighlightingPass> instantiateMainPasses(@NotNull PsiFile psiFile,
                                                                         @NotNull Document document,
                                                                         @NotNull HighlightInfoProcessor highlightInfoProcessor);

  @NotNull
  public abstract List<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories();
}
