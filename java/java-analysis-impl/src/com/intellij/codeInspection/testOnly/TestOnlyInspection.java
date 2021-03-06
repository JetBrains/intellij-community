// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.testOnly;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public class TestOnlyInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getShortName() {
    return "TestOnlyProblems";
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder h, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        validate(expression.getMethodExpression(), expression.resolveMethod(), h);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
        if (reference != null && validate(reference, expression.resolveMethod(), h)) {
          validate(reference, ObjectUtils.tryCast(reference.resolve(), PsiMember.class), h);
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          validate(expression, (PsiMethod)resolve, h);
        }
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

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiMember) {
          PsiAnnotation vft = findVisibleForTestingAnnotation((PsiMember)element);
          if (vft != null && isDirectlyTestOnly((PsiMember)element)) {
            PsiElement toHighlight = null;
            if (element instanceof PsiNameIdentifierOwner) {
              toHighlight = ((PsiNameIdentifierOwner)element).getNameIdentifier();
            }
            if (toHighlight == null) {
              toHighlight = element;
            }
            h.registerProblem(toHighlight, JavaAnalysisBundle.message("visible.for.testing.makes.little.sense.on.test.only.code"), new RemoveAnnotationQuickFix(vft, (PsiModifierListOwner)element));
          }
        }
        super.visitElement(element);
      }
    };
  }

  private static boolean validate(@NotNull PsiElement place, @Nullable PsiMember member, ProblemsHolder h) {
    if (member == null) {
      return true;
    }

    PsiAnnotation vft = findVisibleForTestingAnnotation(member);
    if (vft == null && !isAnnotatedAsTestOnly(member)) {
      return true;
    }
    if (isInsideTestOnlyMethod(place) || isInsideTestOnlyField(place) || isInsideTestOnlyClass(place) || isInsideTestClass(place)
        || isUnderTestSources(place)) {
      return true;
    }

    if (vft != null) {
      String modifier = getAccessModifierWithoutTesting(vft);
      if (modifier == null) {
        modifier = getNextLowerAccessLevel(member);
      }

      LightModifierList modList = new LightModifierList(member.getManager(), JavaLanguage.INSTANCE, modifier);
      if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), modList, place, null, null)) {
        return true;
      }
    }

    reportProblem(place, member, h);
    return false;
  }

  private static final List<String> ourModifiersDescending =
    Arrays.asList(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE);

  private static String getNextLowerAccessLevel(@NotNull PsiMember member) {
    int methodModifier = ContainerUtil.indexOf(ourModifiersDescending, member::hasModifierProperty);
    int minModifier = ourModifiersDescending.size() - 1;
    if (member instanceof PsiMethod) {
      for (PsiMethod superMethod : ((PsiMethod)member).findSuperMethods()) {
        minModifier = Math.min(minModifier, ContainerUtil.indexOf(ourModifiersDescending, superMethod::hasModifierProperty));
      }
    }
    return ourModifiersDescending.get(Math.min(minModifier, methodModifier + 1));
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
    return AnnotationUtil.findAnnotation(member, 
                                         "com.google.common.annotations.VisibleForTesting",
                                         "com.android.annotations.VisibleForTesting",
                                         "org.jetbrains.annotations.VisibleForTesting");
  }

  private static boolean isInsideTestOnlyMethod(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiMethod.class));
  }

  private static boolean isInsideTestOnlyField(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiField.class));
  }

  private static boolean isInsideTestOnlyClass(@NotNull PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiClass.class));
  }

  private static boolean isAnnotatedAsTestOnly(@Nullable PsiMember m) {
    if (m == null) return false;
    return isDirectlyTestOnly(m) || isAnnotatedAsTestOnly(m.getContainingClass());
  }

  private static boolean isDirectlyTestOnly(@NotNull PsiMember m) {
    return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, CHECK_EXTERNAL);
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
    String message = JavaAnalysisBundle.message(target instanceof PsiClass
                                               ? "inspection.test.only.problems.test.only.class.reference"
                                               : target instanceof PsiField ? "inspection.test.only.problems.test.only.field.reference"
                                                                            : "inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}
