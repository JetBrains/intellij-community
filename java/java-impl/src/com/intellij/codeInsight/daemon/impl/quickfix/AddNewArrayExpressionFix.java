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
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddNewArrayExpressionFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  @SafeFieldForPreview
  private final PsiType myType;

  public AddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression initializer) {
    super(initializer);
    myType = getType(initializer);
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
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    doFix();
  }

  public void doFix() {
    PsiArrayInitializerExpression initializer = ObjectUtils.tryCast(getStartElement(), PsiArrayInitializerExpression.class);
    if (initializer == null || myType == null) return;
    Project project = initializer.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    @NonNls String text = "new " + myType.getCanonicalText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression) factory.createExpressionFromText(text, null);
    Objects.requireNonNull(newExpr.getArrayInitializer()).replace(initializer);
    newExpr = (PsiNewExpression) CodeStyleManager.getInstance(project).reformat(newExpr);
    initializer.replace(newExpr);
  }

  private static PsiType getType(@NotNull PsiArrayInitializerExpression initializer) {
    final PsiExpression[] initializers = initializer.getInitializers();
    final PsiElement parent = initializer.getParent();
    if (!(parent instanceof PsiAssignmentExpression)) {
      if (initializers.length == 0) return null;
      return validateType(initializers[0].getType(), parent);
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    final PsiType type = assignmentExpression.getType();
    if (!(type instanceof PsiArrayType)) {
      if (initializers.length == 0) return null;
      return validateType(initializers[0].getType(), parent);
    }
    return validateType(((PsiArrayType)type).getComponentType(), parent);
  }

  private static PsiType validateType(PsiType type, @NotNull PsiElement context) {
    if (PsiType.NULL.equals(type)) return null;
    return LambdaUtil.notInferredType(type) || !PsiTypesUtil.isDenotableType(type, context) ? null
                                                                                   : TypeConversionUtil.erasure(type);
  }
}
