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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 11/20/12
 */
class AddModuleDependencyFix extends OrderEntryFix {
  private final List<Module> myModules = new ArrayList<Module>();
  private final Module myCurrentModule;
  private final VirtualFile myClassVFile;
  private final PsiClass[] myClasses;
  private final PsiReference myReference;
  private static final Logger LOG = Logger.getInstance("#" + AddModuleDependencyFix.class.getName());

  public AddModuleDependencyFix(Module currentModule,
                                VirtualFile classVFile,
                                PsiClass[] classes,
                                PsiReference reference) {
    final PsiElement psiElement = reference.getElement();
    final Project project = psiElement.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    for (PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        myModules.add(classModule);
      }
    }
    myCurrentModule = currentModule;
    myClassVFile = classVFile;
    myClasses = classes;
    myReference = reference;
  }

  @Override
  @NotNull
  public String getText() {
    return myModules.size() == 1 ? QuickFixBundle.message("orderEntry.fix.add.dependency.on.module", myModules.get(0).getName())
                                 : "Add dependency on module...";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    for (Module module : myModules) {
      if (module.isDisposed()) return false;
    }
    return !project.isDisposed() && !myModules.isEmpty() && !myCurrentModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, PsiFile file) {
    if (myModules.size() == 1) {
      addDependencyOnModule(project, editor, myModules.get(0));
    }
    else {
      final JBList list = new JBList(myModules);
      list.setCellRenderer(new ListCellRendererWrapper<Module>() {
        @Override
        public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
          if (module != null) {
            setIcon(ModuleType.get(module).getIcon());
            setText(module.getName());
          }
        }
      });
      final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Choose Module to Add Dependency on")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            final Object value = list.getSelectedValue();
            if (value instanceof Module) {
              addDependencyOnModule(project, editor, (Module)value);
            }
          }
        }).createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      } else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addDependencyOnModule(final Project project, final Editor editor, final Module module) {
    final Runnable doit = new Runnable() {
      @Override
      public void run() {
        final boolean test = ModuleRootManager.getInstance(myCurrentModule).getFileIndex().isInTestSourceContent(myClassVFile);
        ModuleRootModificationUtil.addDependency(myCurrentModule, module,
                                                 test ? DependencyScope.TEST : DependencyScope.COMPILE, false);
        if (editor != null) {
          final List<PsiClass> targetClasses = new ArrayList<PsiClass>();
          for (PsiClass psiClass : myClasses) {
            if (ModuleUtilCore.findModuleForPsiElement(psiClass) == module) {
              targetClasses.add(psiClass);
            }
          }
          new AddImportAction(project, myReference, editor, targetClasses.toArray(new PsiClass[targetClasses.size()])).execute();
        }
      }
    };
    final Pair<Module, Module> circularModules = ModuleCompilerUtil.addingDependencyFormsCircularity(myCurrentModule, module);
    if (circularModules == null) {
      doit.run();
    }
    else {
      showCircularWarningAndContinue(project, circularModules, module, doit);
    }
  }


  private static void showCircularWarningAndContinue(final Project project, final Pair<Module, Module> circularModules,
                                                     final Module classModule,
                                                     final Runnable doit) {
    final String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning", classModule.getName(),
                                                  circularModules.getFirst().getName(), circularModules.getSecond().getName());
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!project.isOpen()) return;
        int ret = Messages.showOkCancelDialog(project, message,
                                              QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning"),
                                              Messages.getWarningIcon());
        if (ret == Messages.OK) {
          ApplicationManager.getApplication().runWriteAction(doit);
        }
      }
    });
  }
}
