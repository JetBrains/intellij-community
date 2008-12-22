package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;

public class HighlightExitPointsHandler extends HighlightUsagesHandlerBase {
  private PsiElement myTarget;

  public HighlightExitPointsHandler(final Editor editor, final PsiFile file, final PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  public void highlightUsages() {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.return");

    PsiElement parent = myTarget.getParent();
    if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return;

    PsiMethod method = PsiTreeUtil.getParentOfType(myTarget, PsiMethod.class);
    if (method == null) return;

    PsiCodeBlock body = method.getBody();
    try {
      highlightExitPoints((PsiStatement)parent, body);
    }
    catch (AnalysisCanceledException e) {
      // ignore
    }
  }

  @Nullable
  private static PsiElement getExitTarget(PsiStatement exitStatement) {
    if (exitStatement instanceof PsiReturnStatement) {
      return PsiTreeUtil.getParentOfType(exitStatement, PsiMethod.class);
    }
    else if (exitStatement instanceof PsiBreakStatement) {
      return ((PsiBreakStatement)exitStatement).findExitedStatement();
    }
    else if (exitStatement instanceof PsiContinueStatement) {
      return ((PsiContinueStatement)exitStatement).findContinuedStatement();
    }
    else if (exitStatement instanceof PsiThrowStatement) {
      final PsiExpression expr = ((PsiThrowStatement)exitStatement).getException();
      if (expr == null) return null;
      final PsiType exceptionType = expr.getType();
      if (!(exceptionType instanceof PsiClassType)) return null;

      PsiElement target = exitStatement;
      while (!(target instanceof PsiMethod || target == null || target instanceof PsiClass || target instanceof PsiFile)) {
        if (target instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)target;
          final PsiParameter[] params = tryStatement.getCatchBlockParameters();
          for (PsiParameter param : params) {
            if (param.getType().isAssignableFrom(exceptionType)) {
              break;
            }
          }

        }
        target = target.getParent();
      }
      if (target instanceof PsiMethod || target instanceof PsiTryStatement) {
        return target;
      }
      return null;
    }

    return null;
  }

  private boolean highlightExitPoints(final PsiStatement parent, final PsiCodeBlock body) throws AnalysisCanceledException {
    final Project project = myTarget.getProject();
    ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);

    Collection<PsiStatement> exitStatements = ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize(), new IntArrayList(),
                                                                                          PsiReturnStatement.class, PsiBreakStatement.class,
                                                                                          PsiContinueStatement.class, PsiThrowStatement.class);
    if (!exitStatements.contains(parent)) return false;

    PsiElement originalTarget = getExitTarget(parent);

    final Iterator<PsiStatement> it = exitStatements.iterator();
    while (it.hasNext()) {
      PsiStatement psiStatement = it.next();
      if (getExitTarget(psiStatement) != originalTarget) {
        it.remove();
      }
    }

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    final boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(myEditor, highlightManager);
    HighlightUsagesHandler.doHighlightElements(myEditor, exitStatements.toArray(new PsiElement[exitStatements.size()]),
                                               attributes, clearHighlights);

    HighlightUsagesHandler.setupFindModel(project);
    String message = clearHighlights ? "" : CodeInsightBundle.message("status.bar.exit.points.highlighted.message", exitStatements.size(),
                                                                      HighlightUsagesHandler.getShortcutText());
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
    return true;
  }
}
