// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class JavaWithCastSurrounder extends JavaExpressionModCommandSurrounder {
  @Override
  public boolean isApplicable(PsiExpression expr) {
    return !PsiTypes.voidType().equals(expr.getType());
  }

  @Override
  protected void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiType[] types =
      DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> GuessManager.getInstance(project).guessTypeToCast(expr));
    final boolean parenthesesNeeded = expr instanceof PsiPolyadicExpression ||
                                      expr instanceof PsiConditionalExpression ||
                                      expr instanceof PsiAssignmentExpression;
    String exprText = parenthesesNeeded ? "(" + expr.getText() + ")" : expr.getText();

    RangeMarker rangeMarker = expr.getUserData(ElementToWorkOn.TEXT_RANGE);
    TextRange range = rangeMarker == null ? expr.getTextRange() : rangeMarker.getTextRange();
    PsiFile file = updater.getWritable(context.file()); // cannot use expr.getContainingFile(), as expr could be detached (e.g., part of polyadic)
    Document document = file.getFileDocument();
    document.replaceString(range.getStartOffset(), range.getEndOffset(), "((x)" + exprText + ")");
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiTypeCastExpression cast = PsiTreeUtil.getParentOfType(file.findElementAt(range.getStartOffset() + 1), PsiTypeCastExpression.class);
    if (cast == null) return;
    cast = (PsiTypeCastExpression)CodeStyleManager.getInstance(project).reformat(cast);
    updater.moveCaretTo(cast.getParent().getTextRange().getEndOffset());
    PsiTypeElement castType = Objects.requireNonNull(cast.getCastType());
    Set<LookupElement> itemSet = new LinkedHashSet<>();
    for (PsiType type : types) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }

    String result = types.length > 0 ? types[0].getPresentableText() : "";
    Expression typeExpr = new ConstantNode(result).withLookupItems(itemSet.size() > 1 ? itemSet : Collections.emptySet());
    updater.templateBuilder()
      .field(castType, typeExpr)
      .finishAt(updater.getCaretOffset());
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.cast.template");
  }
}
