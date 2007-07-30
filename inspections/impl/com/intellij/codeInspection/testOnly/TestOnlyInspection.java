package com.intellij.codeInspection.testOnly;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class TestOnlyInspection extends BaseLocalInspectionTool {
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
    return GroupNames.GENERAL_GROUP_NAME;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder h, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression e) {
        validate(e, h);
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    };
  }

  private void validate(PsiMethodCallExpression e, ProblemsHolder h) {
    if (!isTestOnlyMethodCalled(e)) return;
    if (isInsideTestOnlyMethod(e)) return;
    if (isInsideTestClass(e)) return;
    if (isUnderTestSources(e)) return;

    reportProblem(e, h);
  }

  private boolean isTestOnlyMethodCalled(PsiMethodCallExpression e) {
    PsiMethod m = e.resolveMethod();
    if (m == null) return false;
    return isAnnotatedAsTestOnly(m);
  }

  private boolean isInsideTestOnlyMethod(PsiMethodCallExpression e) {
    PsiMethod m = getTopLevelParentOfType(e, PsiMethod.class);
    return isAnnotatedAsTestOnly(m);
  }

  private boolean isAnnotatedAsTestOnly(PsiMethod m) {
    return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, false);
  }

  private boolean isInsideTestClass(PsiMethodCallExpression e) {
    PsiClass c = getTopLevelParentOfType(e, PsiClass.class);
    return TestUtil.isTestClass(c);
  }

  public static <T extends PsiElement> T getTopLevelParentOfType(PsiElement e, Class<T> c) {
    T parent = PsiTreeUtil.getParentOfType(e, c);
    if (parent == null) return null;

    do {
      T next = PsiTreeUtil.getParentOfType(parent, c);
      if (next == null) return parent;
      parent = next;
    } while(true);
  }

  private boolean isUnderTestSources(PsiMethodCallExpression e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
    VirtualFile f = e.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  private void reportProblem(PsiMethodCallExpression e, ProblemsHolder h) {
    String message = InspectionsBundle.message("inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}