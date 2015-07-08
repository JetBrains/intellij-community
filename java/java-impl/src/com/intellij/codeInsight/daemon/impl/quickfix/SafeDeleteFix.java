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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public SafeDeleteFix(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  @NotNull
  public String getText() {
    PsiElement startElement = getStartElement();
    return QuickFixBundle.message("safe.delete.text", startElement == null ? "" : HighlightMessageUtil.getSymbolName(startElement, PsiSubstitutor.EMPTY));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiElement[] elements = {startElement};
    if (startElement instanceof PsiParameter) {
      SafeDeleteProcessor.createInstance(project, null, elements, false, false, true).run();
    } else {
      SafeDeleteHandler.invoke(project, elements, true);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
