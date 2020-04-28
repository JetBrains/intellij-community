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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddMethodBodyFix implements IntentionAction {
  private final PsiMethod myMethod;

  public AddMethodBodyFix(@NotNull PsiMethod method) {
    myMethod = method;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.method.body.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() &&
           myMethod.getBody() == null &&
           myMethod.getContainingClass() != null &&
           BaseIntentionAction.canModify(myMethod);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myMethod;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiUtil.setModifierProperty(myMethod, PsiModifier.ABSTRACT, false);
    CreateFromUsageUtils.setupMethodBody(myMethod);
    CreateFromUsageUtils.setupEditor(myMethod, editor);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new AddMethodBodyFix(PsiTreeUtil.findSameElementInCopy(myMethod, target));
  }
}
