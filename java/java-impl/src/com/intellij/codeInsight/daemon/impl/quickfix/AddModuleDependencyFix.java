// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PointersKt;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.modules.CircularModuleDependenciesDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 * @since 20.11.2012
 */
class AddModuleDependencyFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final Set<Module> myModules;
  private final DependencyScope myScope;
  private final boolean myExported;
  private final List<SmartPsiElementPointer<PsiClass>> myClasses;

  public AddModuleDependencyFix(PsiReference reference, Module currentModule, DependencyScope scope, List<PsiClass> classes) {
    super(reference);
    myCurrentModule = currentModule;
    myModules = new LinkedHashSet<>();
    myScope = scope;
    myExported = false;
    myClasses = ContainerUtil.map(classes, PointersKt::createSmartPointer);

    PsiElement psiElement = reference.getElement();
    ModuleRootManager rootManager = ModuleRootManager.getInstance(currentModule);
    for (PsiClass aClass : classes) {
      if (isAccessible(aClass, psiElement)) {
        Module classModule = ModuleUtilCore.findModuleForFile(aClass.getContainingFile());
        if (classModule != null && classModule != currentModule && !rootManager.isDependsOn(classModule)) {
          myModules.add(classModule);
        }
      }
    }
  }

  private static boolean isAccessible(PsiClass aClass, PsiElement refElement) {
    return JavaResolveUtil.isAccessible(aClass, aClass.getContainingClass(), aClass.getModifierList(), refElement, aClass, null);
  }

  public AddModuleDependencyFix(PsiJavaModuleReference reference,
                                Module currentModule,
                                Set<Module> modules,
                                DependencyScope scope,
                                boolean exported) {
    super(reference);
    myCurrentModule = currentModule;
    myModules = modules;
    myScope = scope;
    myExported = exported;
    myClasses = Collections.emptyList();
  }

  @Override
  @NotNull
  public String getText() {
    if (myModules.size() == 1) {
      Module module = ContainerUtil.getFirstItem(myModules);
      assert module != null;
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
    Couple<Module> circularModules = CircularModuleDependenciesDetector.addingDependencyFormsCircularity(myCurrentModule, module);
    if (circularModules == null || showCircularWarning(project, circularModules, module)) {
      JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, module, myScope, myExported);

      if (editor != null && !myClasses.isEmpty()) {
        PsiClass[] targetClasses = myClasses.stream()
          .map(SmartPsiElementPointer::getElement)
          .filter(c -> c != null && ModuleUtilCore.findModuleForPsiElement(c) == module)
          .toArray(PsiClass[]::new);
        if (targetClasses.length > 0) {
          PsiReference ref = restoreReference();
          if (ref != null) {
            DumbService.getInstance(project).completeJustSubmittedTasks();
            new AddImportAction(project, ref, editor, targetClasses).execute();
          }
        }
      }
    }
  }

  private static boolean showCircularWarning(Project project, Couple<Module> circle, Module classModule) {
    String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning",
                                            classModule.getName(), circle.getFirst().getName(), circle.getSecond().getName());
    String title = QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning");
    return Messages.showOkCancelDialog(project, message, title, Messages.getWarningIcon()) == Messages.OK;
  }

}