/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public class ReplaceWithListAccessFix implements IntentionAction {
  private PsiArrayAccessExpression myArrayAccessExpression;

  public ReplaceWithListAccessFix(PsiArrayAccessExpression arrayAccessExpression) {
    myArrayAccessExpression = arrayAccessExpression;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("replace.with.list.access.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myArrayAccessExpression.isValid()) return false;
    if (!TypeConversionUtil.areTypesAssignmentCompatible(PsiType.INT, myArrayAccessExpression.getIndexExpression())){
      return false;
    }

    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiType type = arrayExpression.getType();

    if (type == null) return false;

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_LIST, myArrayAccessExpression.getResolveScope());

    if (listClass == null) return false;

    final PsiElementFactory factory = facade.getElementFactory();
    final PsiType listType = factory.createType(listClass);

    return listType.isAssignableFrom(type);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiExpression indexExpression = myArrayAccessExpression.getIndexExpression();

    if (indexExpression == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiElement parent = myArrayAccessExpression.getParent();
    final PsiElement listAccess = factory.createExpressionFromText(arrayExpression.getText() + ".get(" + indexExpression.getText() + ")", parent);
    myArrayAccessExpression.replace(listAccess);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
