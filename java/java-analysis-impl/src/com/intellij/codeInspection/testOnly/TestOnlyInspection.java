/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
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
      @Override
      public void visitCallExpression(PsiCallExpression e) {
        validate(e, e.resolveMethod(), h);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiField) {
          validate(reference, (PsiField)resolve, h);
        }
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        if (reference.getParent() instanceof PsiNewExpression
            || reference.getParent() instanceof PsiAnonymousClass
            || PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) {
          return;
        }
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass) validate(reference, (PsiClass)resolve, h);
      }
    };
  }

  private static void validate(@NotNull PsiElement reference, @Nullable PsiMember member, ProblemsHolder h) {
    if (member == null || !isAnnotatedAsTestOnly(member)) return;
    if (isInsideTestOnlyMethod(reference)) return;
    if (isInsideTestOnlyField(reference)) return;
    if (isInsideTestClass(reference)) return;
    if (isUnderTestSources(reference)) return;

    PsiAnnotation anno = findVisibleForTestingAnnotation(member);
    if (anno != null) {
      String modifier = getAccessModifierWithoutTesting(anno);
      if (modifier == null) {
        modifier = member.hasModifierProperty(PsiModifier.PUBLIC) ? PsiModifier.PROTECTED :
                   member.hasModifierProperty(PsiModifier.PROTECTED) ? PsiModifier.PACKAGE_LOCAL :
                   PsiModifier.PRIVATE;
      }

      LightModifierList modList = new LightModifierList(member.getManager(), JavaLanguage.INSTANCE, modifier);
      if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), modList, reference, null, null)) {
        return;
      }
    }

    reportProblem(reference, member, h);
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
  private static PsiAnnotation findVisibleForTestingAnnotation(@NotNull PsiMember member) {
    PsiAnnotation anno = AnnotationUtil.findAnnotation(member, "com.google.common.annotations.VisibleForTesting");
    if (anno == null) {
      anno = AnnotationUtil.findAnnotation(member, "com.android.annotations.VisibleForTesting");
    }
    if (anno != null) return anno;

    PsiClass containingClass = member.getContainingClass();
    return containingClass != null ? findVisibleForTestingAnnotation(containingClass) : null;
  }

  private static boolean isInsideTestOnlyMethod(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiMethod.class));
  }
  
  private static boolean isInsideTestOnlyField(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiField.class));
  }

  private static boolean isAnnotatedAsTestOnly(@Nullable PsiMember m) {
    if (m == null) return false;
    return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, false, false)
           || findVisibleForTestingAnnotation(m) != null
           || isAnnotatedAsTestOnly(m.getContainingClass());
  }

  private static boolean isInsideTestClass(PsiElement e) {
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

  private static boolean isUnderTestSources(PsiElement e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
    VirtualFile f = e.getContainingFile().getVirtualFile();
    return f != null && rm.getFileIndex().isInTestSourceContent(f);
  }

  private static void reportProblem(PsiElement e, PsiMember target, ProblemsHolder h) {
    String message = InspectionsBundle.message(target instanceof PsiClass
                                               ? "inspection.test.only.problems.test.only.class.reference"
                                               : "inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}
