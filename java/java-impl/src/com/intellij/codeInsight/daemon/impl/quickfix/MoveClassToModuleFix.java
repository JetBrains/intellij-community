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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class MoveClassToModuleFix {
  private MoveClassToModuleFix() {
  }

  public static void registerFixes(QuickFixActionRegistrar registrar, final PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.getElement();
    @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return;
    PsiDirectory dir = containingFile.getContainingDirectory();
    if (dir == null) return;

    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(currentModule).getSourceRoots();
    if (sourceRoots.length == 0) return;
    final PsiDirectory sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceRoots[0]);
    if (sourceDirectory == null) return;

    VirtualFile vsourceRoot = fileIndex.getSourceRootForFile(classVFile);
    if (vsourceRoot == null) return;
    final PsiDirectory sourceRoot = PsiManager.getInstance(project).findDirectory(vsourceRoot);
    if (sourceRoot == null) return;

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass[] classes = facade.getShortNamesCache().getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    for (final PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      final PsiFile psiFile = aClass.getContainingFile();
      if (!(psiFile instanceof PsiJavaFile)) continue;
      PsiJavaFile javaFile = (PsiJavaFile)psiFile;
      final String packageName = javaFile.getPackageName();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        IntentionAction action = new IntentionAction() {
          @NotNull
          public String getText() {
            return "Move '"+aClass.getQualifiedName()+"' from module '" + classModule.getName() +
                   "' to '"+currentModule.getName()+"'";
          }

          @NotNull
          public String getFamilyName() {
            return "move it";
          }

          public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
            return true;
          }

          public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
            RefactoringActionHandler moveHandler = RefactoringActionHandlerFactory.getInstance().createMoveHandler();
            DataManager dataManager = DataManager.getInstance();
            DataContext dataContext = dataManager.getDataContext();
            PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(currentModule, packageName, sourceRoot, true);
            DataContext context = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), directory, dataContext);
            String qualifiedName = aClass.getQualifiedName();
            if (qualifiedName == null) {
              return;
            }
            moveHandler.invoke(project, new PsiElement[]{aClass}, context);
            PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
            PsiClass newClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.moduleScope(currentModule));
            if (reference != null && newClass != null) {
              final QuestionAction action = new AddImportAction(project, reference, editor, newClass);
              action.execute();
            }
          }

          public boolean startInWriteAction() {
            return false;
          }
        };
        registrar.register(action);
      }
    }
  }
}
