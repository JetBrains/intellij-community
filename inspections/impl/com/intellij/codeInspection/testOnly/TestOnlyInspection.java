package com.intellij.codeInspection.testOnly;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class TestOnlyInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.test.only.problems.display.name");
  }

  @NotNull
  public String getShortName() {
    return "TestOnlyProblems";
  }

  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder h, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitCallExpression(PsiCallExpression e) {
        validate(e, h);
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    };
  }

  private void validate(PsiCallExpression e, ProblemsHolder h) {
    if (!isTestOnlyMethodCalled(e)) return;
    if (isInsideTestOnlyMethod(e)) return;
    if (isInsideTestClass(e)) return;
    if (isUnderTestSources(e)) return;

    reportProblem(e, h);
  }

  private boolean isTestOnlyMethodCalled(PsiCallExpression e) {
    PsiMethod m = e.resolveMethod();
    if (m == null) return false;
    return isAnnotatedAsTestOnly(m);
  }

  private boolean isInsideTestOnlyMethod(PsiCallExpression e) {
    PsiMethod m = getTopLevelParentOfType(e, PsiMethod.class);
    return isAnnotatedAsTestOnly(m);
  }

  private boolean isAnnotatedAsTestOnly(PsiMethod m) {
    return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, false);
  }

  private boolean isInsideTestClass(PsiCallExpression e) {
    PsiClass c = getTopLevelParentOfType(e, PsiClass.class);
    return TestUtil.isTestClass(c);
  }

  private <T extends PsiElement> T getTopLevelParentOfType(PsiElement e, Class<T> c) {
    T parent = PsiTreeUtil.getParentOfType(e, c);
    if (parent == null) return null;

    do {
      T next = PsiTreeUtil.getParentOfType(parent, c);
      if (next == null) return parent;
      parent = next;
    }
    while (true);
  }

  private boolean isUnderTestSources(PsiCallExpression e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
    VirtualFile f = e.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  private void reportProblem(PsiCallExpression e, ProblemsHolder h) {
    String message = InspectionsBundle.message("inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}