// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UnstableApiUsageInspection extends LocalInspectionTool {
  public boolean myIgnoreInsideImports = true;

  public final List<String> unstableApiAnnotations = new ExternalizableStringSet(
    "org.jetbrains.annotations.ApiStatus.Experimental",
    "com.google.common.annotations.Beta",
    "io.reactivex.annotations.Beta",
    "io.reactivex.annotations.Experimental",
    "rx.annotations.Experimental",
    "rx.annotations.Beta",
    "org.apache.http.annotation.Beta"
  );

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    SingleCheckboxOptionsPanel checkboxPanel = new SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports");

    //TODO in add annotation window "Include non-project items" should be enabled by default
    JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list"));

    JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(checkboxPanel, BorderLayout.NORTH);
    panel.add(annotationsListControl, BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!isApplicable(holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
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
          if (modifierListOwner == null || !isLibraryElement(modifierListOwner)) {
            continue;
          }

          for (String annotation : unstableApiAnnotations) {
            if (modifierListOwner.hasAnnotation(annotation)) {
              holder.registerProblem(reference,
                                     JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.description", getReferenceText(reference)),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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

  private static boolean isLibraryElement(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }

    PsiFile containingPsiFile = element.getContainingFile();
    if (containingPsiFile == null) {
      return false;
    }
    VirtualFile containingVirtualFile = containingPsiFile.getVirtualFile();
    if (containingVirtualFile == null) {
      return false;
    }
    return ProjectFileIndex.getInstance(element.getProject()).isInLibraryClasses(containingVirtualFile);
  }

  @NotNull
  private static String getReferenceText(@NotNull PsiReference reference) {
    if (reference instanceof PsiQualifiedReference) {
      String referenceName = ((PsiQualifiedReference)reference).getReferenceName();
      if (referenceName != null) {
        return referenceName;
      }
    }
    // references are not PsiQualifiedReference for annotation attributes
    return StringUtil.getShortName(reference.getCanonicalText());
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

  private boolean isApplicable(@NotNull Project project) {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    for (String annotation : unstableApiAnnotations) {
      if (javaPsiFacade.findClass(annotation, scope) != null) {
        return true;
      }
    }
    return false;
  }
}
