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
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuickFixes {
  public static final LocalQuickFixAndIntentionActionOnPsiElement EMPTY_FIX = new LocalQuickFixAndIntentionActionOnPsiElement(null) {
    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @Nullable("is null when called from inspection") Editor editor, @NotNull PsiElement psiElement, @NotNull PsiElement psiElement2) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getText() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      throw new UnsupportedOperationException();
    }
  };

  public static final IntentionAndQuickFixAction EMPTY_ACTION = new IntentionAndQuickFixAction() {
    @NotNull
    @Override
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
      throw new UnsupportedOperationException();
    }
  };

}
