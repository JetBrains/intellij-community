// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ConvertCompactConstructorToCanonicalAction extends PsiUpdateModCommandAction<PsiElement> {
  public ConvertCompactConstructorToCanonicalAction() {
    super(PsiElement.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiMethod method = getMethod(element);
    if (method == null) return;
    PsiClass recordClass = method.getContainingClass();
    if (recordClass == null) return;
    PsiMethod prototype = generateCanonicalConstructor(method);
    PsiCodeBlock oldBody = Objects.requireNonNull(method.getBody());
    PsiCodeBlock body = Objects.requireNonNull(prototype.getBody());
    int offset = context.offset();
    if (oldBody.getTextRange().contains(offset)) {
      offset += body.getTextRangeInParent().getStartOffset() - oldBody.getTextRangeInParent().getStartOffset();
    }
    method.replace(prototype);
    updater.moveTo(offset);
  }

  /**
   * @param compactConstructor compact constructor
   * @return non-physical canonical constructor
   */
  @NotNull
  public static PsiMethod generateCanonicalConstructor(@NotNull PsiMethod compactConstructor) {
    if (!JavaPsiRecordUtil.isCompactConstructor(compactConstructor)) {
      throw new IllegalArgumentException("Compact constructor required");
    }
    PsiClass recordClass = Objects.requireNonNull(compactConstructor.getContainingClass());
    PsiMethod prototype = new RecordConstructorMember(recordClass, false).generateRecordConstructor();
    PsiModifierList modifierList = compactConstructor.getModifierList();
    prototype.getModifierList().replace(modifierList);
    PsiElement beforeModifier = modifierList.getPrevSibling();
    if (beforeModifier != null) {
      prototype.addRangeBefore(compactConstructor.getFirstChild(), beforeModifier, prototype.getModifierList());
    }
    PsiCodeBlock oldBody = Objects.requireNonNull(compactConstructor.getBody());
    PsiCodeBlock body = (PsiCodeBlock)Objects.requireNonNull(prototype.getBody()).replace(oldBody);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(compactConstructor.getProject());
    for (PsiRecordComponent component : recordClass.getRecordComponents()) {
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
      if (field != null && !HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        body.add(factory.createStatementFromText("this." + field.getName() + "=" + field.getName() + ";", body));
      }
    }
    return prototype;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiMethod method = getMethod(element);
    return method != null &&
           JavaPsiRecordUtil.isCompactConstructor(method) &&
           method.getBody() != null &&
           method.getContainingClass() != null &&
           method.getContainingClass().isRecord() ? Presentation.of(getFamilyName()) : null;
  }

  private static PsiMethod getMethod(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiLambdaExpression.class, PsiMember.class);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.convert.compact.constructor.to.canonical");
  }
}
