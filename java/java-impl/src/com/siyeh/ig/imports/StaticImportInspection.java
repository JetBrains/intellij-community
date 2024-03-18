/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.imports;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.*;

public final class StaticImportInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"}) public boolean ignoreSingleFieldImports = false;
  @SuppressWarnings({"PublicField"}) public boolean ignoreSingeMethodImports = false;
  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility
  @SuppressWarnings("PublicField") public OrderedSet<String> allowedClasses = new OrderedSet<>();

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> result = new SmartList<>();
    final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)infos[0];
    final SuppressForTestsScopeFix fix = SuppressForTestsScopeFix.build(this, importStaticStatement);
    ContainerUtil.addIfNotNull(result, fix);
    final PsiClass aClass = importStaticStatement.resolveTargetClass();
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      if (name != null) {
        result.add(new AddToInspectionOptionListFix<>(this, InspectionGadgetsBundle.message("static.import.fix.ignore.class", name), name,
                                                      tool -> tool.allowedClasses));
      }
    }
    result.add(buildFix(infos));
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("allowedClasses", InspectionGadgetsBundle.message("static.import.options.border.title"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("static.import.options.chooserTitle"))),
      checkbox("ignoreSingleFieldImports", InspectionGadgetsBundle.message("ignore.single.field.static.imports.option")),
      checkbox("ignoreSingeMethodImports", InspectionGadgetsBundle.message("ignore.single.method.static.imports.option"))
    );
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.import.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new StaticImportFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticImportVisitor();
  }

  private static class StaticImportFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("static.import.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiImportStaticStatement importStatement = (PsiImportStaticStatement)startElement;
      final PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null) {
        return;
      }
      final JavaResolveResult[] importTargets = importReference.multiResolve(false);
      if (importTargets.length == 0) {
        return;
      }
      final boolean onDemand = importStatement.isOnDemand();
      final StaticImportReferenceCollector referenceCollector = new StaticImportReferenceCollector(importTargets, onDemand);
      final PsiJavaFile file = (PsiJavaFile)importStatement.getContainingFile();
      file.accept(referenceCollector);
      final List<PsiJavaCodeReferenceElement> references = referenceCollector.getReferences();
      final Map<PsiJavaCodeReferenceElement, PsiMember> referenceTargetMap = new HashMap<>();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement target = reference.resolve();
        if (target instanceof PsiEnumConstant &&
            reference instanceof PsiExpression && PsiImplUtil.getSwitchLabel((PsiExpression)reference) != null) continue;
        if (target instanceof PsiMember member) {
          referenceTargetMap.put(reference, member);
        }
      }
      new CommentTracker().deleteAndRestoreComments(importStatement);
      for (Map.Entry<PsiJavaCodeReferenceElement, PsiMember> entry : referenceTargetMap.entrySet()) {
        removeReference(entry.getKey(), entry.getValue());
      }
    }

    private static void removeReference(PsiJavaCodeReferenceElement reference, PsiMember target) {
      final PsiManager manager = reference.getManager();
      final Project project = manager.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiClass aClass = target.getContainingClass();
      if (aClass == null) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      final String qualifiedName = aClass.getQualifiedName();
      final String text = tracker.text(reference);
      final String referenceText = qualifiedName + '.' + text;
      if (reference instanceof PsiReferenceExpression) {
        final PsiElement insertedElement = tracker.replaceAndRestoreComments(reference, referenceText);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
      }
      else {
        final PsiJavaCodeReferenceElement referenceElement =
          factory.createReferenceElementByFQClassName(referenceText, reference.getResolveScope());
        final PsiElement insertedElement = tracker.replaceAndRestoreComments(reference, referenceElement);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(insertedElement);
      }
    }

    static class StaticImportReferenceCollector extends JavaRecursiveElementVisitor {

      private final JavaResolveResult[] importTargets;
      private final boolean onDemand;
      private final List<PsiJavaCodeReferenceElement> references = new ArrayList<>();

      StaticImportReferenceCollector(JavaResolveResult @NotNull [] importTargets, boolean onDemand) {
        this.importTargets = importTargets;
        this.onDemand = onDemand;
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        if (isFullyQualifiedReference(reference)) {
          return;
        }
        PsiElement parent = reference.getParent();
        if (parent instanceof PsiImportStatementBase) {
          return;
        }
        while (parent instanceof PsiJavaCodeReferenceElement) {
          parent = parent.getParent();
          if (parent instanceof PsiImportStatementBase) {
            return;
          }
        }
        checkStaticImportReference(reference);
      }

      private void checkStaticImportReference(PsiJavaCodeReferenceElement reference) {
        if (reference.isQualified()) {
          return;
        }
        final PsiElement target = reference.resolve();
        if (!(target instanceof PsiMethod) && !(target instanceof PsiClass) && !(target instanceof PsiField)) {
          return;
        }
        final PsiMember member = (PsiMember)target;
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        for (JavaResolveResult importTarget : importTargets) {
          final PsiElement targetElement = importTarget.getElement();
          if (targetElement instanceof PsiMethod || targetElement instanceof PsiField) {
            if (member.equals(targetElement)) {
              addReference(reference);
            }
          }
          else if (targetElement instanceof PsiClass) {
            if (onDemand) {
              final PsiClass containingClass = member.getContainingClass();
              if (InheritanceUtil.isInheritorOrSelf((PsiClass)targetElement, containingClass, true)) {
                addReference(reference);
              }
            }
            else {
              if (targetElement.equals(member)) {
                addReference(reference);
              }
            }
          }
        }
      }

      private void addReference(PsiJavaCodeReferenceElement reference) {
        references.add(reference);
      }

      public List<PsiJavaCodeReferenceElement> getReferences() {
        return references;
      }

      public static boolean isFullyQualifiedReference(PsiJavaCodeReferenceElement reference) {
        if (!reference.isQualified()) {
          return false;
        }
        final PsiElement directParent = reference.getParent();
        if (directParent instanceof PsiMethodCallExpression ||
            directParent instanceof PsiAssignmentExpression ||
            directParent instanceof PsiVariable) {
          return false;
        }
        final PsiElement parent =
          PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, PsiPackageStatement.class, JavaCodeFragment.class);
        if (parent != null) {
          return false;
        }
        final PsiElement target = reference.resolve();
        if (!(target instanceof PsiClass aClass)) {
          return false;
        }
        final String fqName = aClass.getQualifiedName();
        if (fqName == null) {
          return false;
        }
        return PsiNameHelper.getQualifiedClassName(reference.getText(), false).equals(fqName);
      }
    }
  }

  private class StaticImportVisitor extends BaseInspectionVisitor {

    @Override
    public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
      super.visitImportStaticStatement(statement);
      if (shouldReportImportStatement(statement)) {
        registerError(statement, statement);
      }
    }

    private boolean shouldReportImportStatement(PsiImportStaticStatement importStatement) {
      final PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null || importReference.multiResolve(false).length == 0) {
        return false;
      }
      PsiClass targetClass = importStatement.resolveTargetClass();
      boolean checked = false;
      while (targetClass != null) {
        final String qualifiedName = targetClass.getQualifiedName();
        if (allowedClasses.contains(qualifiedName)) {
          return false;
        }
        if (checked) {
          break;
        }
        targetClass = targetClass.getContainingClass();
        checked = true;
      }
      if (importStatement.isOnDemand()) {
        return true;
      }
      if (ignoreSingleFieldImports || ignoreSingeMethodImports) {
        boolean field = false;
        boolean method = false;
        // in the presence of method overloading the plain resolve() method returns null
        final JavaResolveResult[] results = importReference.multiResolve(false);
        for (JavaResolveResult result : results) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiField) {
            field = true;
          } else if (element instanceof PsiMethod) {
            method = true;
          }
        }
        if (field && !method) {
          if (ignoreSingleFieldImports) {
            return false;
          }
        }
        else if (method && !field) {
          if (ignoreSingeMethodImports) {
            return false;
          }
        }
      }
      return true;
    }
  }
}
