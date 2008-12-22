package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public class HighlightExceptionsHandlerFactory implements HighlightUsagesHandlerFactory {
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword) {
      PsiElement parent = target.getParent();
      if (PsiKeyword.TRY.equals(target.getText()) && parent instanceof PsiTryStatement) {
        return createHighlightTryHandler(editor, file, target, parent);
      }
      if (PsiKeyword.CATCH.equals(target.getText()) && parent instanceof PsiCatchSection) {
        return createHighlightCatchHandler(editor, file, target, parent);
      }
      if (PsiKeyword.THROWS.equals(target.getText())) {
        return createThrowsHandler(editor, file, target);
      }
    }
    return null;
  }

  private static HighlightUsagesHandlerBase createHighlightTryHandler(final Editor editor,
                                                                      final PsiFile file,
                                                                      final PsiElement target,
                                                                      final PsiElement parent) {
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");
    final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(), tryStatement.getTryBlock());
    return new HighlightExceptionsHandler(editor, file, target, psiClassTypes, tryStatement.getTryBlock(), Condition.TRUE);
  }

  @Nullable
  private static HighlightUsagesHandlerBase createHighlightCatchHandler(final Editor editor,
                                                                 final PsiFile file,
                                                                 final PsiElement target,
                                                                 final PsiElement parent) {
    final PsiCatchSection catchSection = (PsiCatchSection)parent;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiTryStatement tryStatement = catchSection.getTryStatement();

    final PsiParameter param = catchSection.getParameter();
    if (param == null) return null;

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();

    final PsiClassType[] allThrownExceptions = ExceptionUtil.collectUnhandledExceptions(tryStatement.getTryBlock(),
                                                                                        tryStatement.getTryBlock());
    Condition<PsiType> filter = new Condition<PsiType>() {
      public boolean value(PsiType type) {
        for (PsiParameter parameter : catchBlockParameters) {
          boolean isAssignable = parameter.getType().isAssignableFrom(type);
          if (parameter != param) {
            if (isAssignable) return false;
          }
          else {
            return isAssignable;
          }
        }
        return false;
      }
    };

    ArrayList<PsiClassType> filtered = new ArrayList<PsiClassType>();
    for (PsiClassType type : allThrownExceptions) {
      if (filter.value(type)) filtered.add(type);
    }

    return new HighlightExceptionsHandler(editor, file, target, filtered.toArray(new PsiClassType[filtered.size()]), catchSection, filter);
  }

  @Nullable
  private static HighlightUsagesHandlerBase createThrowsHandler(final Editor editor, final PsiFile file, final PsiElement target) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");
    PsiElement grand = target.getParent().getParent();
    if (!(grand instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)grand;
    if (method.getBody() == null) return null;

    final PsiClassType[] psiClassTypes = ExceptionUtil.collectUnhandledExceptions(method.getBody(), method.getBody());
    return new HighlightExceptionsHandler(editor, file, target, psiClassTypes, method.getBody(), Condition.TRUE);
  }
}
