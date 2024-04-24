// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaWithCastSurrounder extends JavaExpressionSurrounder {
  private static final @NonNls String TYPE_TEMPLATE_VARIABLE = "type";

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isApplicable(PsiExpression expr) {
    return !PsiTypes.voidType().equals(expr.getType());
  }

  @Override
  public TextRange surroundExpression(final Project project, final Editor editor, PsiExpression expr) throws IncorrectOperationException {
    assert expr.isValid();
    PsiType[] types = ActionUtil.underModalProgress(
      project,
      CodeInsightBundle.message("surround.with.cast.modal.title"),
      () ->  GuessManager.getInstance(project).guessTypeToCast(expr)
    );
    final boolean parenthesesNeeded = expr instanceof PsiPolyadicExpression ||
                                      expr instanceof PsiConditionalExpression ||
                                      expr instanceof PsiAssignmentExpression;
    String exprText = parenthesesNeeded ? "(" + expr.getText() + ")" : expr.getText();

    TextRange range;
    if (expr.isPhysical()) {
      range = expr.getTextRange();
    } else {
      final RangeMarker rangeMarker = expr.getUserData(ElementToWorkOn.TEXT_RANGE);
      if (rangeMarker == null) return null;
      range = rangeMarker.getTextRange();
    }
    WriteAction.run(() -> {
      final Template template = generateTemplate(project, exprText, types);
      editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
      editor.getCaretModel().moveToOffset(range.getStartOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      TemplateManager.getInstance(project).startTemplate(editor, template);
    });
    return null;
  }

  private static Template generateTemplate(Project project, String exprText, final PsiType[] suggestedTypes) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    Set<LookupElement> itemSet = new LinkedHashSet<>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }

    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], project) : null;

    Expression expr = new ConstantNode(result).withLookupItems(itemSet.size() > 1 ? itemSet : Collections.emptySet());
    template.addTextSegment("((");
    template.addVariable(TYPE_TEMPLATE_VARIABLE, expr, expr, true);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();

    return template;
  }

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.cast.template");
  }
}
