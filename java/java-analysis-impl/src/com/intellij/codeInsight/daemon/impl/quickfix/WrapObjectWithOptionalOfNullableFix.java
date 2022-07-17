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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class WrapObjectWithOptionalOfNullableFix extends MethodArgumentFix implements HighPriorityAction {
  public static final ArgumentFixerActionFactory REGISTAR = new MyFixerActionFactory();

  protected WrapObjectWithOptionalOfNullableFix(final @NotNull PsiExpressionList list,
                                                final int i,
                                                final @NotNull PsiType toType,
                                                final @NotNull ArgumentFixerActionFactory fixerActionFactory) {
    super(list, i, toType, fixerActionFactory);
  }

  @NotNull
  @Override
  public String getText() {
    PsiExpressionList list = myArgList.getElement();
    if (list != null && list.getExpressionCount() == 1) {
      return QuickFixBundle.message("wrap.with.optional.single.parameter.text");
    }
    else {
      return QuickFixBundle.message("wrap.with.optional.parameter.text", myIndex + 1);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiExpressionList list = myArgList.getElement();
    if (list == null) return null;
    return new WrapObjectWithOptionalOfNullableFix(PsiTreeUtil.findSameElementInCopy(list, target), myIndex, myToType,
                                                   myArgumentFixerActionFactory);
  }

  public static IntentionAction createFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return new MyFix(expression, type);
  }

  private static class MyFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
    @SafeFieldForPreview // used only in isAvailable
    private final PsiType myType;

    protected MyFix(@Nullable PsiElement element, @Nullable PsiType type) {
      super(element);
      myType = type;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("wrap.with.optional.single.parameter.text");
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      startElement.replace(getModifiedExpression((PsiExpression)getStartElement()));
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      return BaseIntentionAction.canModify(startElement) &&
             PsiUtil.isLanguageLevel8OrHigher(startElement) && areConvertible(((PsiExpression) startElement).getType(), myType);
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }
  }

  public static class MyFixerActionFactory extends ArgumentFixerActionFactory {

    @Nullable
    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      return getModifiedExpression(expression);
    }

    @Override
    public boolean areTypesConvertible(@NotNull final PsiType exprType, @NotNull final PsiType parameterType, @NotNull final PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || areConvertible(exprType, parameterType);
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new WrapObjectWithOptionalOfNullableFix(list, i, toType, this);
    }
  }

  private static boolean areConvertible(@Nullable PsiType exprType, @Nullable PsiType parameterType) {
    if (exprType == null ||
        !exprType.isValid() ||
        !(parameterType instanceof PsiClassType) ||
        !parameterType.isValid()) {
      return false;
    }
    final PsiClassType.ClassResolveResult resolve = ((PsiClassType)parameterType).resolveGenerics();
    final PsiClass resolvedClass = resolve.getElement();
    if (resolvedClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(resolvedClass.getQualifiedName())) return false;

    final Collection<PsiType> values = resolve.getSubstitutor().getSubstitutionMap().values();
    if (values.isEmpty()) return true;
    if (values.size() > 1) return false;
    final PsiType optionalTypeParameter = ContainerUtil.getFirstItem(values);
    if (optionalTypeParameter == null) return false;
    return TypeConversionUtil.isAssignable(optionalTypeParameter, exprType);
  }

  @NotNull
  private static PsiExpression getModifiedExpression(PsiExpression expression) {
    final Project project = expression.getProject();
    final Nullability nullability = NullabilityUtil.getExpressionNullability(expression, true);
    String methodName = nullability == Nullability.NOT_NULL ? "of" : "ofNullable";
    final String newExpressionText = CommonClassNames.JAVA_UTIL_OPTIONAL + "." + methodName + "(" + expression.getText() + ")";
    return JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText, expression);
  }
}
