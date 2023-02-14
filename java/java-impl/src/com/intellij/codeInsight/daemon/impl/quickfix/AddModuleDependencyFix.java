// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PointersKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.modules.CircularModuleDependenciesDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author anna
 */
class AddModuleDependencyFix extends OrderEntryFix {
  private final Module myCurrentModule;
  private final Set<? extends Module> myModules;
  private final DependencyScope myScope;
  private final boolean myExported;
  private final List<SmartPsiElementPointer<PsiClass>> myClasses;

  AddModuleDependencyFix(@NotNull PsiReference reference, @NotNull Module currentModule, @NotNull DependencyScope scope, @NotNull List<? extends PsiClass> classes) {
    super(reference);
    myCurrentModule = currentModule;
    LinkedHashSet<Module> modules = new LinkedHashSet<>();
    myScope = scope;
    myExported = false;
    myClasses = ContainerUtil.map(classes, PointersKt::createSmartPointer);

    PsiElement psiElement = reference.getElement();
    ModuleRootManager rootManager = ModuleRootManager.getInstance(currentModule);
    for (PsiClass aClass : classes) {
      if (isAccessible(aClass, psiElement)) {
        Module classModule = ModuleUtilCore.findModuleForFile(aClass.getContainingFile());
        if (classModule != null && classModule != currentModule && !dependsWithScope(rootManager, classModule, scope)) {
          modules.add(classModule);
        }
      }
    }
    myModules = modules;
  }

  AddModuleDependencyFix(@NotNull PsiJavaModuleReference reference, @NotNull Module currentModule, @NotNull Set<? extends Module> modules, @NotNull DependencyScope scope, boolean exported) {
    super(reference);
    myCurrentModule = currentModule;
    myModules = modules;
    myScope = scope;
    myExported = exported;
    myClasses = Collections.emptyList();
  }

  private static boolean dependsWithScope(@NotNull ModuleRootManager rootManager, Module classModule, DependencyScope scope) {
    return ContainerUtil.exists(rootManager.getOrderEntries(),
                                entry -> entry instanceof ModuleOrderEntry && classModule.equals(((ModuleOrderEntry)entry).getModule()) &&
                                         (scope == DependencyScope.TEST || scope == ((ModuleOrderEntry)entry).getScope()));
  }

  private static boolean isAccessible(PsiClass aClass, PsiElement refElement) {
    return JavaResolveUtil.isAccessible(aClass, aClass.getContainingClass(), aClass.getModifierList(), refElement, aClass, null);
  }

  @Override
  @NotNull
  public String getText() {
    if (myModules.size() == 1) {
      Module module = ContainerUtil.getFirstItem(myModules);
      assert module != null;
      return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module", module.getName());
    }
    return QuickFixBundle.message("orderEntry.fix.add.dependency.on.module.choose");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() &&
           !myCurrentModule.isDisposed() &&
           !myModules.isEmpty() &&
           !ContainerUtil.exists(myModules, Module::isDisposed);
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
    if (myModules.size() == 1) {
      addDependencyOnModule(project, editor, ContainerUtil.getFirstItem(myModules));
    }
    else {
      JBPopup popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(new ArrayList<>(myModules))
        .setRenderer(new ModuleListCellRenderer())
        .setTitle(QuickFixBundle.message("orderEntry.fix.choose.module.to.add.dependency.on"))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback(selectedValue -> {
          if (selectedValue != null) {
            addDependencyOnModule(project, editor, selectedValue);
          }
        })
        .createPopup();
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      }
      else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(
      HtmlChunk.text(JavaBundle.message("adds.module.dependencies.preview",
                                         myModules.size(),
                                         ContainerUtil.getFirstItem(myModules).getName(),
                                         NlsMessages.formatAndList(ContainerUtil.map2List(myModules, module -> "'" + module.getName() + "'")),
                                         myCurrentModule.getName())));
  }

  private void addDependencyOnModule(@NotNull Project project, Editor editor, @NotNull Module module) {
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

  private static boolean showCircularWarning(@NotNull Project project, @NotNull Couple<Module> circle, @NotNull Module classModule) {
    String message = QuickFixBundle.message("orderEntry.fix.circular.dependency.warning",
                                            classModule.getName(), circle.getFirst().getName(), circle.getSecond().getName());
    String title = QuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning");
    return Messages.showOkCancelDialog(project, message, title, Messages.getWarningIcon()) == Messages.OK;
  }

}