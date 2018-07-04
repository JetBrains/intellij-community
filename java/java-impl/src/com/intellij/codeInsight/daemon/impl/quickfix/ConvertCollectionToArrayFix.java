/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class ConvertCollectionToArrayFix implements IntentionAction {
  private final PsiExpression myCollectionExpression;
  private final PsiExpression myExpressionToReplace;
  private final String myNewArrayText;

  public ConvertCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                     @NotNull PsiExpression expressionToReplace,
                                     @NotNull PsiArrayType arrayType) {
    myCollectionExpression = collectionExpression;
    myExpressionToReplace = expressionToReplace;

    PsiType componentType = arrayType.getComponentType();
    myNewArrayText = componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? "" : "new " + getArrayTypeText(componentType);
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("collection.to.array.text", myNewArrayText);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("collection.to.array.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myCollectionExpression.isValid() && PsiManager.getInstance(project).isInProject(myCollectionExpression) &&
        myExpressionToReplace.isValid() && PsiManager.getInstance(project).isInProject(myExpressionToReplace);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String replacement = ParenthesesUtils.getText(myCollectionExpression, ParenthesesUtils.POSTFIX_PRECEDENCE) +
                         ".toArray(" + myNewArrayText + ")";
    myExpressionToReplace.replace(factory.createExpressionFromText(replacement, myCollectionExpression));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  private static String getArrayTypeText(PsiType componentType) {
    if (componentType instanceof PsiArrayType) {
      return getArrayTypeText(((PsiArrayType)componentType).getComponentType()) + "[]";
    }
    if (componentType instanceof PsiClassType) {
      return ((PsiClassType)componentType).rawType().getCanonicalText() + "[0]";
    }
    return componentType.getCanonicalText() + "[0]";
  }
}
