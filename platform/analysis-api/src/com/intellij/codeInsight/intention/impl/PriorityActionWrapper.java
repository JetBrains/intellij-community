/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PriorityActionWrapper extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final LocalQuickFixAndIntentionActionOnPsiElement fix;

  private PriorityActionWrapper(PsiElement element, @NotNull LocalQuickFixAndIntentionActionOnPsiElement fix) {
    super(element);
    this.fix = fix;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return fix.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return fix.isAvailable(project, file, startElement, endElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    fix.invoke(project, file, editor, startElement, endElement);
  }

  @Override
  public boolean startInWriteAction() {
    return fix.startInWriteAction();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return fix.getElementToMakeWritable(file);
  }

  @NotNull
  @Override
  public String getText() {
    return fix.getName();
  }

  private static class LowPriorityLocalQuickFixWrapper extends PriorityActionWrapper implements LowPriorityAction {
    protected LowPriorityLocalQuickFixWrapper(PsiElement element, @NotNull LocalQuickFixAndIntentionActionOnPsiElement fix) {
      super(element, fix);
    }
  }

  @NotNull
  public static LocalQuickFixAndIntentionActionOnPsiElement lowPriority(PsiElement element, @NotNull LocalQuickFixAndIntentionActionOnPsiElement fix) {
    return new LowPriorityLocalQuickFixWrapper(element, fix);
  }
}
