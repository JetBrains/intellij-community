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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author anna
 * @since 20.11.2012
 */
class AddModuleDependencyFix extends AddOrderEntryFix {
  private static final Logger LOG = Logger.getInstance(AddModuleDependencyFix.class);

  private final Module myCurrentModule;
  private final VirtualFile myRefVFile;
  private final List<PsiClass> myClasses;
  private final Set<Module> myModules;

  public AddModuleDependencyFix(Module currentModule, VirtualFile refVFile, List<PsiClass> classes, PsiReference reference) {
    super(reference);
    myCurrentModule = currentModule;
    myRefVFile = refVFile;
    myClasses = classes;
    myModules = new LinkedHashSet<>();

    final PsiElement psiElement = reference.getElement();
    final Project project = psiElement.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(currentModule);
    for (PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !rootManager.isDependsOn(classModule)) {
        myModules.add(classModule);
      }
    }
  }

  public AddModuleDependencyFix(Module currentModule, VirtualFile refVFile, Set<Module> modules, PsiReference reference) {
    super(reference);
    myCurrentModule = currentModule;
    myRefVFile = refVFile;
    myClasses = Collections.emptyList();
    myModules = modules;
  }

  @Override
  @NotNull
  public String getText() {
    if (myModules.size() == 1) {
      final Module module = ContainerUtil.getFirstItem(myModules);
      LOG.assertTrue(module != null);
      return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module", module.getName());
    }
    else {
      return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module.choose");
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed() && !myModules.isEmpty() && myModules.stream().noneMatch(Module::isDisposed);
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
    if (myModules.size() == 1) {
      addDependencyOnModule(project, editor, ContainerUtil.getFirstItem(myModules));
    }
    else {
      JBList<Module> list = new JBList<>(myModules);
      list.setCellRenderer(new ModuleListCellRenderer());
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(QuickFixBundle.message("orderEntry.fix.choose.module.to.add.dependency.on"))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(() -> addDependencyOnModule(project, editor, list.getSelectedValue()))
        .createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      }
      else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addDependencyOnModule(Project project, Editor editor, @Nullable Module module) {
    if (module == null) return;
    Couple<Module> circularModules = ModuleCompilerUtil.addingDependencyFormsCircularity(myCurrentModule, module);
    if (circularModules == null || showCircularWarning(project, circularModules, module)) {
      WriteAction.run(() -> {
        boolean test = ModuleRootManager.getInstance(myCurrentModule).getFileIndex().isInTestSourceContent(myRefVFile);
        DependencyScope scope = test ? DependencyScope.TEST : DependencyScope.COMPILE;
        JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, module, scope);

        if (editor != null && !myClasses.isEmpty()) {
          PsiClass[] targetClasses = myClasses.stream()
            .filter(c -> ModuleUtilCore.findModuleForPsiElement(c) == module)
            .toArray(PsiClass[]::new);
          if (targetClasses.length > 0 && !DumbService.isDumb(project)) {
            new AddImportAction(project, myReference, editor, targetClasses).execute();
          }
        }
      });
    }
  }

  private static boolean showCircularWarning(Project project, Couple<Module> circle, Module classModule) {
    String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning",
                                            classModule.getName(), circle.getFirst().getName(), circle.getSecond().getName());
    String title = QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning");
    return Messages.showOkCancelDialog(project, message, title, Messages.getWarningIcon()) == Messages.OK;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}