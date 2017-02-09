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
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WrapStringWithFileFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  public final static MyMethodArgumentFixerFactory REGISTAR = new MyMethodArgumentFixerFactory();

  @Nullable private final PsiType myType;

  public WrapStringWithFileFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    super(expression);
    myType = type;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.with.java.io.file.text");
  }


  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType != null &&
           myType.isValid() &&
           myType.equalsToText(CommonClassNames.JAVA_IO_FILE) &&
           startElement.isValid() &&
           startElement.getManager().isInProject(startElement) &&
           isStringType(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    startElement.replace(getModifiedExpression(startElement));
  }

  private static boolean isStringType(@NotNull PsiElement expression) {
    if (!(expression instanceof PsiExpression)) return false;
    final PsiType type = ((PsiExpression) expression).getType();
    if (type == null) return false;
    return type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  private static PsiElement getModifiedExpression(@NotNull PsiElement expression) {
    return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(PsiKeyword.NEW + " " + CommonClassNames.JAVA_IO_FILE + "(" + expression.getText() + ")", expression);
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
             ? QuickFixBundle.message("wrap.with.java.io.file.parameter.single.text")
             : QuickFixBundle.message("wrap.with.java.io.file.parameter.multiple.text", myIndex + 1);
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
      return isStringType(expression) && toType.equalsToText(CommonClassNames.JAVA_IO_FILE) ? (PsiExpression)getModifiedExpression(expression) : null;
    }

    @Override
    public boolean areTypesConvertible(@NotNull final PsiType exprType, @NotNull final PsiType parameterType, @NotNull final PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || (parameterType.equalsToText(CommonClassNames.JAVA_IO_FILE) && exprType.equalsToText(CommonClassNames.JAVA_LANG_STRING));
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new MyMethodArgumentFix(list, i, toType, this);
    }
  }
}
