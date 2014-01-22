package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.postfix.util.Aliases;
import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

@Aliases(".inst")
public class InstanceofExpressionPostfixTemplate extends PostfixTemplate {
  public InstanceofExpressionPostfixTemplate() {
    super("instanceof", "Surrounds expression with instanceof", "expr instanceof SomeType ? ((SomeType) expr). : null");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return getTopmostExpression(context) != null;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    if (expression == null) return;
    surroundExpression(context.getProject(), editor, expression);
  }

  private static void surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    assert expr.isValid();
    PsiType[] types = GuessManager.getInstance(project).guessTypeToCast(expr);
    final boolean parenthesesNeeded = expr instanceof PsiPolyadicExpression ||
                                      expr instanceof PsiConditionalExpression ||
                                      expr instanceof PsiAssignmentExpression;
    String exprText = parenthesesNeeded ? "(" + expr.getText() + ")" : expr.getText();
    Template template = generateTemplate(project, exprText, types);
    TextRange range;
    if (expr.isPhysical()) {
      range = expr.getTextRange();
    }
    else {
      RangeMarker rangeMarker = expr.getUserData(ElementToWorkOn.TEXT_RANGE);
      if (rangeMarker == null) {
        PostfixTemplatesUtils.showErrorHint(project, editor);
        return;
      }
      range = new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    }
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editor.getCaretModel().moveToOffset(range.getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  private static Template generateTemplate(Project project, String exprText, PsiType[] suggestedTypes) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    Set<LookupElement> itemSet = new LinkedHashSet<LookupElement>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);
    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], project) : null;

    Expression expr = new Expression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };

    template.addTextSegment(exprText);
    template.addTextSegment(" instanceof ");
    String type = "type";
    template.addVariable(type, expr, expr, true);
    template.addTextSegment(" ? ((");
    template.addVariableSegment(type);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();
    template.addTextSegment(" : null;");

    return template;
  }
}