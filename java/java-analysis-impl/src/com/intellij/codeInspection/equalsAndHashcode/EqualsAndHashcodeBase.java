// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EqualsAndHashcodeBase extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    final Project project = holder.getProject();
    Pair<PsiMethod, PsiMethod> pair = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass psiObjectClass = ReadAction
        .compute(() -> psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project)));
      if (psiObjectClass == null) {
        return CachedValueProvider.Result.create(null, ProjectRootManager.getInstance(project));
      }
      PsiMethod[] methods = psiObjectClass.getMethods();
      PsiMethod myEquals = null;
      PsiMethod myHashCode = null;
      for (PsiMethod method : methods) {
        final @NonNls String name = method.getName();
        if ("equals".equals(name)) {
          myEquals = method;
        }
        else if ("hashCode".equals(name)) {
          myHashCode = method;
        }
      }
      return CachedValueProvider.Result.create(Pair.create(myEquals, myHashCode), psiObjectClass);
    });

    if (pair == null) return PsiElementVisitor.EMPTY_VISITOR;

    //jdk wasn't configured for the project
    final PsiMethod myEquals = pair.first;
    final PsiMethod myHashCode = pair.second;
    if (myEquals == null || myHashCode == null || !myEquals.isValid() || !myHashCode.isValid()) return PsiElementVisitor.EMPTY_VISITOR;

    return new JavaElementVisitor() {
      @Override public void visitClass(@NotNull PsiClass aClass) {
        super.visitClass(aClass);
        boolean [] hasEquals = {false};
        boolean [] hasHashCode = {false};
        processClass(aClass, hasEquals, hasHashCode, myEquals, myHashCode);
        if (hasEquals[0] != hasHashCode[0]) {
          if (hasHashCode[0] && aClass.isRecord()) {
            // Probably better distributed hashCode is implemented for a record class where default equals works fine
            return;
          }
          PsiIdentifier identifier = aClass.getNameIdentifier();
          holder.registerProblem(identifier != null ? identifier : aClass,
                                 hasEquals[0]
                                  ? JavaAnalysisBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor", "<code>equals()</code>", "<code>hashCode()</code>")
                                  : JavaAnalysisBundle.message("inspection.equals.hashcode.only.one.defined.problem.descriptor","<code>hashCode()</code>", "<code>equals()</code>"),
                                  buildFixes(isOnTheFly, hasEquals[0]));
        }
      }
    };
  }

  private static void processClass(final PsiClass aClass,
                                   final boolean[] hasEquals,
                                   final boolean[] hasHashCode,
                                   PsiMethod equals, PsiMethod hashcode) {
    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (MethodSignatureUtil.areSignaturesEqual(method, equals) && !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        hasEquals[0] = true;
      }
      else if (MethodSignatureUtil.areSignaturesEqual(method, hashcode) && !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        hasHashCode[0] = true;
      }
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return "";
  }

  @Override
  public @NotNull String getShortName() {
    return "EqualsAndHashcode";
  }

  protected LocalQuickFix[] buildFixes(boolean isOnTheFly, boolean hasEquals) {
    return LocalQuickFix.EMPTY_ARRAY;
  }
}
