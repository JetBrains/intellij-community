/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleExportsPackageToItselfInspection extends BaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      if (javaFile.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9) && javaFile.getModuleDeclaration() != null) {
        return new ExportedToSelfVisitor(holder);
      }
    }
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  private static class ExportedToSelfVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public ExportedToSelfVisitor(ProblemsHolder holder) { myHolder = holder; }

    @Override
    public void visitExportsStatement(PsiExportsStatement statement) {
      super.visitExportsStatement(statement);
      PsiJavaModule javaModule = PsiTreeUtil.getParentOfType(statement, PsiJavaModule.class);
      if (javaModule != null) {
        String moduleName = javaModule.getModuleName();
        List<PsiJavaModuleReferenceElement> referenceElements = ContainerUtil.newArrayList(statement.getModuleReferences());
        for (PsiJavaModuleReferenceElement referenceElement : referenceElements) {
          if (moduleName.equals(referenceElement.getReferenceText())) {
            String message = InspectionsBundle.message("inspection.module.exports.package.to.itself.message");
            myHolder.registerProblem(referenceElement, message,
                                     new DeleteExportsToModuleFix(referenceElement));
          }
        }
      }
    }
  }

  private static class DeleteExportsToModuleFix implements LocalQuickFix {
    private final String myModuleName;

    public DeleteExportsToModuleFix(PsiJavaModuleReferenceElement reference) {
      myModuleName = reference.getReferenceText();
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("exports.to.itself.delete.module.fix.name", myModuleName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("exports.to.itself.delete.module.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
      psiElement.delete();
    }
  }
}
