/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class WrapLongWithMathToIntExactFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  public final static MyMethodArgumentFixerFactory REGISTAR = new MyMethodArgumentFixerFactory();

  private final PsiType myType;

  public WrapLongWithMathToIntExactFix(final PsiType type, final @NotNull PsiExpression expression) {
    super(expression);
    myType = type;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    startElement.replace(getModifiedExpression(startElement));
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return startElement.isValid() &&
           startElement.getManager().isInProject(startElement) &&
           PsiUtil.isLanguageLevel8OrHigher(startElement) &&
           areSameTypes(myType, PsiType.INT) &&
           areSameTypes(((PsiExpression) startElement).getType(), PsiType.LONG);
  }

  private static boolean areSameTypes(@Nullable PsiType type, @NotNull PsiPrimitiveType expected) {
    return !(type == null ||
             !type.isValid() ||
             (!type.equals(expected) && !expected.getBoxedTypeName().equals(type.getCanonicalText(false))));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.long.with.math.to.int.text");
  }

  private static PsiElement getModifiedExpression(PsiElement expression) {
    return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText("java.lang.Math.toIntExact(" + expression.getText() + ")", expression);
  }

  private static class MyMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction {

    protected MyMethodArgumentFix(@NotNull PsiExpressionList list,
                                  int i,
                                  @NotNull PsiType toType,
                                  @NotNull ArgumentFixerActionFactory fixerActionFactory) {
      super(list, i, toType, fixerActionFactory);
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return myArgList.getExpressions().length == 1
             ? QuickFixBundle.message("wrap.long.with.math.to.int.parameter.single.text")
             : QuickFixBundle.message("wrap.long.with.math.to.int.parameter.multiple.text", myIndex + 1);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
    }
  }

  public static class MyMethodArgumentFixerFactory extends ArgumentFixerActionFactory {
    @Nullable
    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      return areSameTypes(toType, PsiType.INT) ? (PsiExpression)getModifiedExpression(expression) : null;
    }

    @Override
    public boolean areTypesConvertible(final PsiType exprType, final PsiType parameterType, @NotNull final PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || (areSameTypes(parameterType, PsiType.INT) && areSameTypes(exprType, PsiType.LONG));
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new MyMethodArgumentFix(list, i, toType, this);
    }
  }
}
