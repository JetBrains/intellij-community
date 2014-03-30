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
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LocalQuickFixAndIntentionActionOnPsiElement extends LocalQuickFixOnPsiElement implements IntentionAction {
  protected LocalQuickFixAndIntentionActionOnPsiElement(@Nullable PsiElement element) {
    this(element, element);
  }
  protected LocalQuickFixAndIntentionActionOnPsiElement(@Nullable PsiElement startElement, @Nullable PsiElement endElement) {
    super(startElement, endElement);
  }

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (file == null||myStartElement==null) return;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    if (startElement == null || endElement == null) return;
    invoke(project, file, editor, startElement, endElement);
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myStartElement == null) return false;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    return startElement != null &&
           endElement != null &&
           startElement.isValid() &&
           (endElement == startElement || endElement.isValid()) &&
           file != null &&
           isAvailable(project, file, startElement, endElement);
  }

  public abstract void invoke(@NotNull Project project,
                              @NotNull PsiFile file,
                              @Nullable("is null when called from inspection") Editor editor,
                              @NotNull PsiElement startElement,
                              @NotNull PsiElement endElement);

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    invoke(project, file, null, startElement, endElement);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
