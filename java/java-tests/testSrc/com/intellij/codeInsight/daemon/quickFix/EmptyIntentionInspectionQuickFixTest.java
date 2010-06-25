package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
public class EmptyIntentionInspectionQuickFixTest extends LightQuickFixTestCase{
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/emptyIntention";
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalInspectionTool() {
      @Nls
      @NotNull
      public String getGroupDisplayName() {
        return "MyGroup";
      }

      @Nls
      @NotNull
      public String getDisplayName() {
        return "My";
      }

      @NonNls
      @NotNull
      public String getShortName() {
        return getDisplayName();
      }

      @NotNull
      public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {

          }
          @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
            final String s = (String)expression.getValue();
            if (s.contains("a")) holder.registerProblem(expression, "Look ma! This String contains 'a'");
            if (s.contains("b")) holder.registerProblem(expression, "Look ma! This String contains 'b'");
          }
        };
      }
    }};
  }

  public void testX() throws Exception {
    configureByFile(getBasePath()+"/X.java");
    List<IntentionAction> emptyActions = getAvailableActions();
    for (int i = emptyActions.size()-1; i>=0; i--) {
      IntentionAction action = emptyActions.get(i);
      if (!(action instanceof EmptyIntentionAction)) emptyActions.remove(i);
    }
    assertEquals(1, emptyActions.size());
  }
}
