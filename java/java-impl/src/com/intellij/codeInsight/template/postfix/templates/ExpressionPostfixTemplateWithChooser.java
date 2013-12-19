package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ignatov
 */
public abstract class ExpressionPostfixTemplateWithChooser extends PostfixTemplate {
  protected ExpressionPostfixTemplateWithChooser(@NotNull String name, @NotNull String description, @NotNull String example) {
    super(name, description, example);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return !getExpressions(context, copyDocument, newOffset).isEmpty();
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    List<PsiExpression> expressions = getExpressions(context, editor.getDocument(), editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
    }
    else if (expressions.size() == 1) {
      doIt(editor, expressions.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor, expressions,
        new Pass<PsiExpression>() {
          public void pass(@NotNull final PsiExpression e) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                CommandProcessor.getInstance().executeCommand(e.getProject(), new Runnable() {
                  public void run() {
                    doIt(editor, e);
                  }
                }, "Expand postfix template", PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
              }
            });
          }
        },
        new PsiExpressionTrimRenderer.RenderFunction(),
        "Expressions", 0, ScopeHighlighter.NATURAL_RANGER);
    }
  }

  @NotNull
  protected List<PsiExpression> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
    List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(context.getContainingFile(), document, offset, false);
    return ContainerUtil.filter(expressions.isEmpty() ? maybeTopmostExpression(context) : expressions, getTypeCondition());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected Condition<PsiExpression> getTypeCondition() {
    return Condition.TRUE;
  }

  @NotNull
  private static List<PsiExpression> maybeTopmostExpression(@NotNull PsiElement context) {
    PsiExpression expression = getTopmostExpression(context);
    PsiType type = expression != null ? expression.getType() : null;
    if (type == null || PsiType.VOID.equals(type)) return ContainerUtil.emptyList();
    return ContainerUtil.createMaybeSingletonList(expression);
  }

  protected abstract void doIt(@NotNull Editor editor, @NotNull PsiExpression expression);
}
