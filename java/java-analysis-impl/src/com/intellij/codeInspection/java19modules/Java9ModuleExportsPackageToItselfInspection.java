/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleExportsPackageToItselfInspection extends BaseJavaLocalInspectionTool {
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
        for (PsiJavaModuleReferenceElement referenceElement : statement.getModuleReferences()) {
          if (moduleName.equals(referenceElement.getReferenceText())) {
            String message = InspectionsBundle.message("inspection.module.exports.package.to.itself");
            String fixText = InspectionsBundle.message("exports.to.itself.delete.module.fix.name", moduleName);
            myHolder.registerProblem(referenceElement, message, new DeleteElementFix(referenceElement, fixText));
          }
        }
      }
    }
  }
}