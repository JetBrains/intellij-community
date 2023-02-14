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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ChangeToAppendFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionAction {
  @SafeFieldForPreview
  private final IElementType myTokenType;
  @SafeFieldForPreview
  private final PsiType myLhsType;
  @SafeFieldForPreview
  private volatile TypeInfo myTypeInfo;

  public ChangeToAppendFix(@NotNull IElementType eqOpSign, @NotNull PsiType lType, @NotNull PsiAssignmentExpression assignmentExpression) {
    super(assignmentExpression);
    myTokenType = eqOpSign;
    myLhsType = lType;
  }

  @Override
  public @Nullable PsiAssignmentExpression getStartElement() {
    return ObjectUtils.tryCast(super.getStartElement(), PsiAssignmentExpression.class);
  }

  @NotNull
  @Override
  public String getText() {
    PsiAssignmentExpression assignmentExpression = getStartElement();
    if (assignmentExpression == null) {
      return getFamilyName();
    }
    return QuickFixBundle.message("change.to.append.text",
                                  ChangeToAppendUtil.buildAppendExpression(assignmentExpression.getRExpression(),
                                                                           getTypeInfo().useStringValueOf,
                                                                           new StringBuilder(assignmentExpression.getLExpression().getText())));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("change.to.append.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return JavaTokenType.PLUSEQ == myTokenType && getTypeInfo().appendable;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiAssignmentExpression assignmentExpression = getStartElement();
    if (assignmentExpression == null) return;
    final PsiExpression appendExpression =
      ChangeToAppendUtil.buildAppendExpression(assignmentExpression.getLExpression(), assignmentExpression.getRExpression());
    if (appendExpression == null) return;
    assignmentExpression.replace(appendExpression);
  }

  @NotNull
  private TypeInfo getTypeInfo() {
    if (myTypeInfo != null) return myTypeInfo;
    myTypeInfo = calculateTypeInfo();
    return myTypeInfo;
  }

  @NotNull
  private TypeInfo calculateTypeInfo() {
    if (myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER) ||
        myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
      return new TypeInfo(true, false);
    }
    if (InheritanceUtil.isInheritor(myLhsType, "java.lang.Appendable")) {
      return new TypeInfo(true, true);
    }
    return new TypeInfo(false, false);
  }

  private record TypeInfo(boolean appendable, boolean useStringValueOf) {
  }
}
