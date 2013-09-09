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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class RemoveQualifierFix implements IntentionAction {
  private final PsiExpression myQualifier;
  private final PsiReferenceExpression myExpression;
  private final PsiClass myResolved;

  public RemoveQualifierFix(@NotNull PsiExpression qualifier, @NotNull PsiReferenceExpression expression, @NotNull PsiClass resolved) {
    myQualifier = qualifier;
    myExpression = expression;
    myResolved = resolved;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.qualifier.action.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      myQualifier.isValid()
      && myQualifier.getManager().isInProject(myQualifier)
      && myExpression.isValid()
      && myResolved.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    myQualifier.delete();
    myExpression.bindToElement(myResolved);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
