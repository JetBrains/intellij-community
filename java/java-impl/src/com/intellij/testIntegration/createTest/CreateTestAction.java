/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.HashSet;
import java.util.Set;

public class CreateTestAction extends PsiElementBaseIntentionAction {

  private static final String CREATE_TEST_IN_THE_SAME_ROOT = "create.test.in.the.same.root";

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

    Module srcModule = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (srcModule == null) return false;

    if (psiClass.isAnnotationType() ||
        psiClass instanceof PsiAnonymousClass) {
      return false;
    }
    return true;
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    final Module srcModule = ModuleUtilCore.findModuleForPsiElement(element);
    final PsiClass srcClass = getContainingClass(element);

    if (srcClass == null) return;

    PsiDirectory srcDir = element.getContainingFile().getContainingDirectory();
    PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final HashSet<VirtualFile> testFolders = new HashSet<VirtualFile>();
    checkForTestRoots(srcModule, testFolders);
    if (testFolders.isEmpty() && !propertiesComponent.getBoolean(CREATE_TEST_IN_THE_SAME_ROOT, false)) {
      if (Messages.showOkCancelDialog(project, "Create test in the same source root?", "No Test Roots Found", Messages.getQuestionIcon()) != Messages.OK) {
        return;
      }

      propertiesComponent.setValue(CREATE_TEST_IN_THE_SAME_ROOT, String.valueOf(true));
    }

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
        TestFramework framework = d.getSelectedTestFrameworkDescriptor();
        TestGenerator generator = TestGenerators.INSTANCE.forLanguage(framework.getLanguage());
        generator.generateTest(project, d);
      }
    }, CodeInsightBundle.message("intention.create.test"), this);
  }

  protected static void checkForTestRoots(Module srcModule, Set<VirtualFile> testFolders) {
    checkForTestRoots(srcModule, testFolders, new HashSet<Module>());
  }

  private static void checkForTestRoots(final Module srcModule, final Set<VirtualFile> testFolders, final Set<Module> processed) {
    final boolean isFirst = processed.isEmpty();
    if (!processed.add(srcModule)) return;

    testFolders.addAll(ModuleRootManager.getInstance(srcModule).getSourceRoots(JavaSourceRootType.TEST_SOURCE));
    if (isFirst && !testFolders.isEmpty()) return;

    final HashSet<Module> modules = new HashSet<Module>();
    ModuleUtilCore.collectModulesDependsOn(srcModule, modules);
    for (Module module : modules) {
      checkForTestRoots(module, testFolders, processed);
    }
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