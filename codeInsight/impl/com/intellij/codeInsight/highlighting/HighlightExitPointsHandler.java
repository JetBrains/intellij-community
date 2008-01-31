package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
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

import java.util.ArrayList;
import java.util.List;

public class HighlightExitPointsHandler implements HighlightUsagesHandlerDelegate {
  public boolean highlightUsages(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword) {
      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        PsiElement parent = target.getParent();
        if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return true;

        PsiMethod method = PsiTreeUtil.getParentOfType(target, PsiMethod.class);
        if (method == null) return true;

        PsiCodeBlock body = method.getBody();
        try {
          return highlightExitPoints(target, parent, body, editor);
        }
        catch (AnalysisCanceledException e) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean highlightExitPoints(final PsiElement target, final PsiElement parent, final PsiCodeBlock body,
                                             final Editor editor) throws AnalysisCanceledException {
    final Project project = target.getProject();
    ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);

    List<PsiStatement> exitStatements = new ArrayList<PsiStatement>();
    ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize(), new IntArrayList(),
                                                exitStatements,
                                                new Class[]{PsiReturnStatement.class, PsiBreakStatement.class,
                                                            PsiContinueStatement.class, PsiThrowStatement.class,
                                                            PsiExpressionStatement.class});
    if (!exitStatements.contains(parent)) return true;

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    final boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(editor, highlightManager);
    HighlightUsagesHandler.doHighlightElements(highlightManager, editor, exitStatements.toArray(new PsiElement[exitStatements.size()]),
                                               attributes, clearHighlights);

    HighlightUsagesHandler.setupFindModel(project);
    String message = clearHighlights ? "" : CodeInsightBundle.message("status.bar.exit.points.highlighted.message", exitStatements.size(),
                                                                      HighlightUsagesHandler.getShortcutText());
    WindowManager.getInstance().getStatusBar(project).setInfo(message);
    return false;
  }
}
