// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.util.List;

/**
 * This class can be extended by inspections that should report usage of elements annotated with some particular annotation(s).
 *
 * @since 2018.3
 */
public abstract class AnnotatedElementInspectionBase extends LocalInspectionTool {
  public boolean myIgnoreInsideImports = true;


  @NotNull
  protected abstract List<String> getAnnotations();

  protected abstract void createProblem(@NotNull PsiReference reference, @NotNull ProblemsHolder holder);

  protected boolean shouldProcessElement(@NotNull PsiModifierListOwner element) {
    return true;
  }


  @NotNull
  @Override
  public JPanel createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!isApplicable(holder.getFile(), holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiLanguageInjectionHost) {
          return; // better performance
        }

        if (myIgnoreInsideImports && isInsideImport(element)) {
          return;
        }

        // Java constructors must be handled a bit differently (works fine with Kotlin)
        PsiMethod resolvedConstructor = null;
        PsiElement elementParent = element.getParent();
        if (elementParent instanceof PsiConstructorCall) {
          resolvedConstructor = ((PsiConstructorCall)elementParent).resolveConstructor();
        }

        for (PsiReference reference : element.getReferences()) {
          PsiModifierListOwner modifierListOwner = getModifierListOwner(reference, resolvedConstructor);
          if (modifierListOwner == null || !shouldProcessElement(modifierListOwner)) {
            continue;
          }

          for (String annotation : getAnnotations()) {
            if (modifierListOwner.hasAnnotation(annotation)) {
              createProblem(reference, holder);
              return;
            }
          }
        }
      }
    };
  }

  private static boolean isInsideImport(@NotNull PsiElement element) {
    return PsiTreeUtil.findFirstParent(element, parent -> UastContextKt.toUElement(parent, UImportStatement.class) != null) != null;
  }

  @Nullable
  private static PsiModifierListOwner getModifierListOwner(@NotNull PsiReference reference, @Nullable PsiMethod resolvedConstructor) {
    if (resolvedConstructor != null) {
      return resolvedConstructor;
    }

    if (reference instanceof ResolvingHint && !((ResolvingHint)reference).canResolveTo(PsiModifierListOwner.class)) {
      return null;
    }

    PsiElement resolvedElement = reference.resolve();
    if (resolvedElement instanceof PsiModifierListOwner) {
      return (PsiModifierListOwner)resolvedElement;
    }
    return null;
  }

  private boolean isApplicable(@Nullable PsiFile file, @Nullable Project project) {
    if (file == null || project == null) {
      return false;
    }

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = file.getResolveScope();
    for (String annotation : getAnnotations()) {
      if (javaPsiFacade.findClass(annotation, scope) != null) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  protected static String getReferenceText(@NotNull PsiReference reference) {
    if (reference instanceof PsiQualifiedReference) {
      String referenceName = ((PsiQualifiedReference)reference).getReferenceName();
      if (referenceName != null) {
        return referenceName;
      }
    }
    // references are not PsiQualifiedReference for annotation attributes
    return StringUtil.getShortName(reference.getCanonicalText());
  }
}