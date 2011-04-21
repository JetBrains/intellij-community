/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.create.test");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!isAvailableForElement(element)) return false;

    PsiClass psiClass = getContainingClass(element);

    assert psiClass != null;
    PsiElement leftBrace = psiClass.getLBrace();
    if (leftBrace == null) return false;
    if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

    //TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    //if (!declarationRange.contains(element.getTextRange())) return false;

    return true;
  }

  public static boolean isAvailableForElement(PsiElement element) {
    if (Extensions.getExtensions(TestFramework.EXTENSION_NAME).length == 0) return false;

    if (element == null) return false;

    PsiClass psiClass = getContainingClass(element);

    if (psiClass == null) return false;

    Module srcModule = ModuleUtil.findModuleForPsiElement(psiClass);
    if (srcModule == null) return false;

    if (psiClass.isAnnotationType() ||
        psiClass.isInterface() ||
        psiClass instanceof PsiAnonymousClass ||
        PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null || // inner
        isUnderTestSources(psiClass)) {
      return false;
    }
    return true;
  }

  private static boolean isUnderTestSources(PsiClass c) {
    ProjectRootManager rm = ProjectRootManager.getInstance(c.getProject());
    VirtualFile f = c.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    final Module srcModule = ModuleUtil.findModuleForPsiElement(file);
    final PsiClass srcClass = getContainingClass(element);

    if (srcClass == null) return;

    PsiDirectory srcDir = file.getContainingDirectory();
    PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    final CreateTestDialog d = new CreateTestDialog(project,
                                                    getText(),
                                                    srcClass,
                                                    srcPackage,
                                                    srcModule);
    d.show();
    if (!d.isOK()) return;

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        final TestGenerator generator = d.getGenerator();
        generator.generateTest(project, d);
      }
    }, CodeInsightBundle.message("intention.create.test"), this);
  }

  @Nullable
  private static PsiClass getContainingClass(PsiElement element) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (psiClass == null) {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PsiClassOwner){
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) {
          return classes[0];
        }
      }
    }
    return psiClass;
  }

  public boolean startInWriteAction() {
    return false;
  }
}