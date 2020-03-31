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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AddNewArrayExpressionFix implements IntentionAction {
  private final PsiArrayInitializerExpression myInitializer;
  private final PsiType myType;

  public AddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression initializer) {
    myInitializer = initializer;
    myType = getType();
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.new.array.text", myType.getPresentableText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.new.array.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myInitializer.isValid() || !BaseIntentionAction.canModify(myInitializer)) return false;
    return myType != null;
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myInitializer;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiManager manager = file.getManager();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    @NonNls String text = "new " + myType.getCanonicalText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression) factory.createExpressionFromText(text, null);
    newExpr.getArrayInitializer().replace(myInitializer);
    newExpr = (PsiNewExpression) CodeStyleManager.getInstance(manager.getProject()).reformat(newExpr);
    myInitializer.replace(newExpr);
  }

  private PsiType getType() {
    final PsiExpression[] initializers = myInitializer.getInitializers();
    final PsiElement parent = myInitializer.getParent();
    if (!(parent instanceof PsiAssignmentExpression)) {
      if (initializers.length <= 0) return null;
      return validateType(initializers[0].getType(), parent);
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    final PsiType type = assignmentExpression.getType();
    if (!(type instanceof PsiArrayType)) {
      if (initializers.length <= 0) return null;
      return validateType(initializers[0].getType(), parent);
    }
    return validateType(((PsiArrayType)type).getComponentType(), parent);
  }

  private static PsiType validateType(PsiType type, @NotNull PsiElement context) {
    if (PsiType.NULL.equals(type)) return null;
    return LambdaUtil.notInferredType(type) || !PsiTypesUtil.isDenotableType(type, context) ? null
                                                                                   : TypeConversionUtil.erasure(type);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
