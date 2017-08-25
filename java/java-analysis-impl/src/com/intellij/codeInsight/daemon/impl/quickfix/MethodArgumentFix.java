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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public abstract class MethodArgumentFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(MethodArgumentFix.class);

  protected final PsiExpressionList myArgList;
  protected final int myIndex;
  protected final ArgumentFixerActionFactory myArgumentFixerActionFactory;
  protected final PsiType myToType;

  protected MethodArgumentFix(@NotNull PsiExpressionList list, int i, @NotNull PsiType toType, @NotNull ArgumentFixerActionFactory fixerActionFactory) {
    myArgList = list;
    myIndex = i;
    myArgumentFixerActionFactory = fixerActionFactory;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      myToType != null &&
      myToType.isValid() &&
      myArgList.getExpressions().length > myIndex &&
      myArgList.getExpressions()[myIndex] != null &&
      myArgList.getExpressions()[myIndex].isValid();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiExpression expression = myArgList.getExpressions()[myIndex];

    LOG.assertTrue(expression != null && expression.isValid());
    PsiExpression modified = myArgumentFixerActionFactory.getModifiedArgument(expression, myToType);
    LOG.assertTrue(modified != null, myArgumentFixerActionFactory);
    expression.replace(modified);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.argument.family");
  }
}
