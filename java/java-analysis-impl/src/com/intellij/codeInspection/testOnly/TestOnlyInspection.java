/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.testOnly;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestOnlyInspection extends BaseJavaBatchLocalInspectionTool {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.test.only.problems.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "TestOnlyProblems";
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder h, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitCallExpression(PsiCallExpression e) {
        validate(e, h);
      }
    };
  }

  private static void validate(PsiCallExpression e, ProblemsHolder h) {
    PsiMethod method = e.resolveMethod();

    if (method == null || !isAnnotatedAsTestOnly(method)) return;
    if (isInsideTestOnlyMethod(e)) return;
    if (isInsideTestClass(e)) return;
    if (isUnderTestSources(e)) return;

    PsiAnnotation anno = findVisibleForTestingAnnotation(method);
    if (anno != null) {
      String modifier = getAccessModifierWithoutTesting(anno);
      if (modifier == null) {
        modifier = method.hasModifierProperty(PsiModifier.PUBLIC) ? PsiModifier.PROTECTED :
                   method.hasModifierProperty(PsiModifier.PROTECTED) ? PsiModifier.PACKAGE_LOCAL :
                   PsiModifier.PRIVATE;
      }
      
      LightModifierList modList = new LightModifierList(method.getManager(), JavaLanguage.INSTANCE, modifier);
      if (JavaResolveUtil.isAccessible(method, method.getContainingClass(), modList, e, null, null)) {
        return;
      }
    }

    reportProblem(e, h);
  }

  @Nullable
  private static String getAccessModifierWithoutTesting(PsiAnnotation anno) {
    PsiAnnotationMemberValue ref = anno.findAttributeValue("visibility");
    if (ref instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)ref).resolve();
      if (target instanceof PsiEnumConstant) {
        String name = ((PsiEnumConstant)target).getName();
        return "PRIVATE".equals(name) ? PsiModifier.PRIVATE : "PROTECTED".equals(name) ? PsiModifier.PROTECTED : PsiModifier.PACKAGE_LOCAL;
      }
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findVisibleForTestingAnnotation(@NotNull PsiMethod method) {
    PsiAnnotation anno = AnnotationUtil.findAnnotation(method, "com.google.common.annotations.VisibleForTesting");
    return anno != null ? anno : AnnotationUtil.findAnnotation(method, "com.android.annotations.VisibleForTesting");
  }

  private static boolean isInsideTestOnlyMethod(PsiCallExpression e) {
    PsiMethod m = getTopLevelParentOfType(e, PsiMethod.class);
    return isAnnotatedAsTestOnly(m);
  }

  private static boolean isAnnotatedAsTestOnly(@Nullable PsiMethod m) {
    return m != null && (AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, false, false) || findVisibleForTestingAnnotation(m) != null);
  }

  private static boolean isInsideTestClass(PsiCallExpression e) {
    PsiClass c = getTopLevelParentOfType(e, PsiClass.class);
    return c != null && TestFrameworks.getInstance().isTestClass(c);
  }

  private static <T extends PsiElement> T getTopLevelParentOfType(PsiElement e, Class<T> c) {
    T parent = PsiTreeUtil.getParentOfType(e, c);
    if (parent == null) return null;

    do {
      T next = PsiTreeUtil.getParentOfType(parent, c);
      if (next == null) return parent;
      parent = next;
    }
    while (true);
  }

  private static boolean isUnderTestSources(PsiCallExpression e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
    VirtualFile f = e.getContainingFile().getVirtualFile();
    return f != null && rm.getFileIndex().isInTestSourceContent(f);
  }

  private static void reportProblem(PsiCallExpression e, ProblemsHolder h) {
    String message = InspectionsBundle.message("inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}
