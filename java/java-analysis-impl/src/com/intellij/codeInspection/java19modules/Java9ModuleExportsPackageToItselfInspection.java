// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleExportsPackageToItselfInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return PsiUtil.isModuleFile(holder.getFile()) ? new ExportedToSelfVisitor(holder) : PsiElementVisitor.EMPTY_VISITOR;
  }

  private static class ExportedToSelfVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public ExportedToSelfVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitPackageAccessibilityStatement(PsiPackageAccessibilityStatement statement) {
      super.visitPackageAccessibilityStatement(statement);
      PsiJavaModule javaModule = PsiTreeUtil.getParentOfType(statement, PsiJavaModule.class);
      if (javaModule != null) {
        String moduleName = javaModule.getName();
        List<PsiJavaModuleReferenceElement> references = ContainerUtil.newArrayList(statement.getModuleReferences());
        for (PsiJavaModuleReferenceElement referenceElement : references) {
          if (moduleName.equals(referenceElement.getReferenceText())) {
            String message = InspectionsBundle.message("inspection.module.exports.package.to.itself");
            if (references.size() == 1) {
              String fixText = InspectionsBundle.message("exports.to.itself.delete.statement.fix");
              myHolder.registerProblem(referenceElement, message, QuickFixFactory.getInstance().createDeleteFix(statement, fixText));
            }
            else {
              String fixText = InspectionsBundle.message("exports.to.itself.delete.module.ref.fix", moduleName);
              myHolder.registerProblem(referenceElement, message, QuickFixFactory.getInstance().createDeleteFix(referenceElement, fixText));
            }
          }
        }
      }
    }
  }
}