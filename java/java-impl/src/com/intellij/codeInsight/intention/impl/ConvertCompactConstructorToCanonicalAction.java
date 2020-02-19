// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ConvertCompactConstructorToCanonicalAction extends PsiElementBaseIntentionAction {
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethod method = getMethod(element);
    if (method == null) return;
    PsiClass recordClass = method.getContainingClass();
    if (recordClass == null) return;
    PsiMethod prototype = new RecordConstructorMember(recordClass, false).generateRecordConstructor();
    PsiModifierList modifierList = method.getModifierList();
    prototype.getModifierList().replace(modifierList);
    PsiElement beforeModifier = modifierList.getPrevSibling();
    if (beforeModifier != null) {
      prototype.addRangeBefore(method.getFirstChild(), beforeModifier, prototype.getModifierList());
    }
    PsiCodeBlock oldBody = Objects.requireNonNull(method.getBody());
    PsiCodeBlock body = (PsiCodeBlock)Objects.requireNonNull(prototype.getBody()).replace(oldBody);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    for (PsiRecordComponent component : recordClass.getRecordComponents()) {
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
      if (field != null && !HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        body.add(factory.createStatementFromText("this." + field.getName() + "=" + field.getName() + ";", body));
      }
    }
    int offset = editor.getCaretModel().getOffset();
    if (oldBody.getTextRange().contains(offset)) {
      offset += body.getTextRangeInParent().getStartOffset() - oldBody.getTextRangeInParent().getStartOffset();
    }
    method.replace(prototype);
    editor.getCaretModel().moveToOffset(offset);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiMethod method = getMethod(element);
    return method != null &&
           JavaPsiRecordUtil.isCompactConstructor(method) &&
           method.getBody() != null &&
           method.getContainingClass() != null &&
           method.getContainingClass().isRecord();
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
