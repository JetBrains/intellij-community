// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.JavaIdeCodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StaticImportCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    Project project = holder.getProject();
    JavaProjectCodeInsightSettings settings = JavaProjectCodeInsightSettings.getSettings(project);
    JavaProjectCodeInsightSettings.AutoStaticNameContainer autoStaticNames = settings.getAllIncludedAutoStaticNames();
    if (autoStaticNames.includedNames().isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    Set<String> shortNames = StreamEx.of(autoStaticNames.includedNames())
      .map(name -> StringUtil.getShortName(name))
      .append(autoStaticNames.includedNames().stream()
                .map(name -> StringUtil.getShortName(StringUtil.getPackageName(name)))
                .filter(name -> !name.isBlank()))
      .toSet();

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
        StaticImportContext context = findOnDemandImportContext(expression);
        if (context == null) return;
        List<LocalQuickFix> fixes = new ArrayList<>();
        fixes.add(new StaticImportFix());
        fixes.add(new DeleteSettingsFromAutoImportTable(context.fqn, project));

        JavaProjectCodeInsightSettings projectSettings = JavaProjectCodeInsightSettings.getSettings(project);
        JavaIdeCodeInsightSettings ideSettings = JavaIdeCodeInsightSettings.getInstance();
        String packageName = StringUtil.getPackageName(context.fqn);
        if (!projectSettings.includedAutoStaticNames.contains(context.fqn) &&
            projectSettings.includedAutoStaticNames.contains(packageName) ||
            !ideSettings.includedAutoStaticNames.contains(context.fqn) &&
            ideSettings.includedAutoStaticNames.contains(packageName)) {
          fixes.add(new AddExclusionFromAutoImportTable(context.fqn, project));
        }
        holder.registerProblem(qualifierReference,
                               JavaBundle.message("inspection.static.import.can.be.used.display.name"),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    };
  }

  private static class AddExclusionFromAutoImportTable extends ModCommandQuickFix {
    private final @NotNull String myFqn;
    private final boolean myIsProjectSettings;

    private AddExclusionFromAutoImportTable(@NotNull String fqn, @NotNull Project project) {
      myFqn = fqn;
      JavaProjectCodeInsightSettings settings = JavaProjectCodeInsightSettings.getSettings(project);
      myIsProjectSettings = settings.includedAutoStaticNames.contains(myFqn) ||
                            settings.includedAutoStaticNames.contains(StringUtil.getPackageName(myFqn));
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return ModCommand.nop();
      if (myIsProjectSettings) {
        return ModCommand.updateOptionList(element,
                                           "JavaProjectCodeInsightSettings.includedAutoStaticNames",
                                           strings -> {
                                             strings.add("-" + myFqn);
                                           });
      }
      return ModCommand.updateOptionList(element,
                                         "JavaIdeCodeInsightSettings.includedAutoStaticNames",
                                         strings -> {
                                           strings.add("-" + myFqn);
                                         });
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.name", myFqn,
                                myIsProjectSettings ? 0 : 1);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.static.import.can.be.used.exclude.from.auto.import.family.name");
    }
  }

  private static class DeleteSettingsFromAutoImportTable extends ModCommandQuickFix {
    private final @NotNull String myFqn;
    private final boolean myIsProjectSettings;

    private DeleteSettingsFromAutoImportTable(@NotNull String fqn, @NotNull Project project) {
      myFqn = fqn;
      JavaProjectCodeInsightSettings settings = JavaProjectCodeInsightSettings.getSettings(project);
      myIsProjectSettings = settings.includedAutoStaticNames.contains(myFqn) ||
                            settings.includedAutoStaticNames.contains(StringUtil.getPackageName(myFqn));
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return ModCommand.nop();
      String packageName = StringUtil.getPackageName(myFqn);
      if (myIsProjectSettings) {
        return ModCommand.updateOptionList(element,
                                           "JavaProjectCodeInsightSettings.includedAutoStaticNames",
                                           strings -> {
                                             strings.removeIf(t ->
                                                                t.equals(myFqn) ||
                                                                t.equals(packageName) ||
                                                                t.equals("-" + packageName)
                                             );
                                           });
      }
      return ModCommand.updateOptionList(element,
                                         "JavaIdeCodeInsightSettings.includedAutoStaticNames",
                                         strings -> {
                                           strings.removeIf(t ->
                                                              t.equals(myFqn) ||
                                                              t.equals(packageName) ||
                                                              t.equals("-" + packageName)
                                           );
                                         });
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.name", myFqn, myIsProjectSettings ? 0 : 1);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.static.import.can.be.used.remove.from.auto.import.family.name");
    }
  }

  private static class StaticImportFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      if (!(referenceExpression.getParent() instanceof PsiReferenceExpression targetReferenceExpression)) return;
      JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      javaCodeStyleManager.shortenClassReferences(targetReferenceExpression);
      javaCodeStyleManager.optimizeImports(targetReferenceExpression.getContainingFile());
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.static.import.can.be.used.fix.name");
    }
  }

  /**
   * @param reference the reference expression to evaluate; must not be null.
   * @return the {@link StaticImportContext} if the reference can be on-demand static imported,
   * or null if the criteria for such a context are not met.
   */
  @Nullable
  public static StaticImportCanBeUsedInspection.StaticImportContext findOnDemandImportContext(@NotNull PsiReferenceExpression reference) {
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
    String fqn = classQualifiedName + "." + memberName;
    if (!javaCodeStyleManager.isStaticAutoImportName(fqn)) return null;
    PsiElement referenceNameElement = qualifier.getReferenceNameElement();
    if (referenceNameElement == null) return null;
    PsiClass anImport = AddOnDemandStaticImportAction.getClassToPerformStaticImport(referenceNameElement);
    if (anImport == null) return null;
    if (javaCodeStyleManager.hasConflictingOnDemandImport(javaFile, anImport, memberName)) return null;
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return null;
    return new StaticImportContext(anImport, memberName, importList, fqn);
  }

  public record StaticImportContext(@NotNull PsiClass psiClass,
                                    @NotNull String memberName,
                                    @NotNull PsiImportList importList,
                                    @NotNull String fqn) {
  }
}
