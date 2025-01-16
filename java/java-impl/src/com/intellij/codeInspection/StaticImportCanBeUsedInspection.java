// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class StaticImportCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    JavaProjectCodeInsightSettings settings = JavaProjectCodeInsightSettings.getSettings(holder.getProject());
    List<String> names = settings.getAllIncludedAutoStaticNames();
    if (names.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    Set<String> shortNames = names.stream()
      .map(name -> StringUtil.getShortName(name))
      .collect(Collectors.toSet());
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        PsiElement reference = expression.getReferenceNameElement();
        if (reference == null) return;
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression qualifierReferenceExpression)) return;
        String name = qualifierReferenceExpression.getReferenceName();
        PsiElement qualifierReference = qualifierReferenceExpression.getReferenceNameElement();
        if (qualifierReference == null || name == null || !shortNames.contains(name)) return;
        OnDemandStaticImportContext context = findOnDemandImportContext(expression);
        if (context == null) return;
        holder.registerProblem(qualifierReference,
                               JavaBundle.message("inspection.static.import.can.be.used.display.name"),
                               new OnDemandStaticImportFix());
      }
    };
  }

  private static class OnDemandStaticImportFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      if (!(referenceExpression.getParent() instanceof PsiReferenceExpression targetReferenceExpression)) return;
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(targetReferenceExpression);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.static.import.can.be.used.fix.name");
    }
  }

  /**
   * @param reference the reference expression to evaluate; must not be null.
   * @return the {@link OnDemandStaticImportContext} if the reference can be on-demand static imported,
   * or null if the criteria for such a context are not met.
   */
  @Nullable
  public static OnDemandStaticImportContext findOnDemandImportContext(@NotNull PsiReferenceExpression reference) {
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(reference.getProject());
    PsiFile file = reference.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) return null;
    if (reference instanceof PsiMethodReferenceExpression) return null;
    String memberName = reference.getReferenceName();
    if (memberName == null) return null;
    PsiJavaCodeReferenceElement qualifier = ObjectUtils.tryCast(reference.getQualifier(), PsiJavaCodeReferenceElement.class);
    if (qualifier == null) return null;
    if (GenericsUtil.isGenericReference(reference, qualifier)) return null;
    if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) return null;
    if (!(qualifier.resolve() instanceof PsiClass psiClass)) return null;
    String classQualifiedName = psiClass.getQualifiedName();
    if (!javaCodeStyleManager.isStaticAutoImportClass(classQualifiedName)) return null;
    PsiElement referenceNameElement = qualifier.getReferenceNameElement();
    if (referenceNameElement == null) return null;
    PsiClass anImport = AddOnDemandStaticImportAction.getClassToPerformStaticImport(referenceNameElement);
    if (anImport == null) return null;
    if (javaCodeStyleManager.hasConflictingOnDemandImport(javaFile, anImport, memberName)) return null;
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return null;
    return new OnDemandStaticImportContext(anImport, memberName, importList);
  }

  public record OnDemandStaticImportContext(@NotNull PsiClass psiClass, @NotNull String memberName, @NotNull PsiImportList importList) {
  }
}
