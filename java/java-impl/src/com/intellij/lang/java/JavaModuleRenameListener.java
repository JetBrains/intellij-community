// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.AutomaticRenamingDialog;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;

public class JavaModuleRenameListener implements ProjectComponent, ModuleListener {
  public JavaModuleRenameListener(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, this);
  }

  @Override
  public void modulesRenamed(@NotNull Project project, @NotNull List<Module> modules, @NotNull Function<Module, String> oldNameProvider) {
    List<Pair<SmartPsiElementPointer<PsiJavaModule>, String>> suggestions = new ArrayList<>();

    for (Module module : modules) {
      if (ModuleType.get(module) == JavaModuleType.getModuleType()) {
        PsiJavaModule javaModule = Stream.of(ModuleRootManager.getInstance(module).getSourceRoots(false))
            .map(root -> JavaModuleGraphUtil.findDescriptorByFile(root, project))
            .filter(Objects::nonNull)
            .findFirst().orElse(null);
        if (javaModule != null && javaModule.getName().equals(LightJavaModule.moduleName(oldNameProvider.fun(module)))) {
          suggestions.add(pair(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaModule),
                           LightJavaModule.moduleName(module.getName())));
        }
      }
    }

    if (!suggestions.isEmpty()) {
      AppUIExecutor.onUiThread(ModalityState.NON_MODAL)
          .later()
          .inSmartMode(project)
          .inTransaction(project)
          .execute(() -> renameModules(project, suggestions));
    }
  }

  private static void renameModules(Project project, List<Pair<SmartPsiElementPointer<PsiJavaModule>, String>> suggestions) {
    MyAutomaticRenamer renamer = new MyAutomaticRenamer();
    for (Pair<SmartPsiElementPointer<PsiJavaModule>, String> rename : suggestions) {
      PsiJavaModule javaModule = rename.first.getElement();
      if (javaModule != null) {
        renamer.addElement(javaModule, rename.second);
      }
    }

    if (!renamer.getElements().isEmpty()) {
      AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(project, renamer);
      dialog.showOptionsPanel();

      if (dialog.showAndGet()) {
        RenameProcessor processor = null;

        for (Map.Entry<PsiNamedElement, String> entry : renamer.getRenames().entrySet()) {
          String newName = entry.getValue();
          if (newName != null) {
            if (processor == null) {
              processor = new RenameProcessor(project, entry.getKey(), newName, dialog.isSearchInComments(), dialog.isSearchTextOccurrences());
            }
            else {
              processor.addElement(entry.getKey(), newName);
            }
          }
        }

        if (processor != null) {
          processor.run();
        }
      }
    }
  }

  private static class MyAutomaticRenamer extends AutomaticRenamer {
    private void addElement(PsiJavaModule module, String newName) {
      myElements.add(module);
      suggestAllNames(module.getName(), newName);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDialogTitle() {
      return RefactoringBundle.message("auto.rename.module.dialog.title");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    public String getDialogDescription() {
      return RefactoringBundle.message("auto.rename.module.dialog.description");
    }

    @Override
    public String entityName() {
      return RefactoringBundle.message("auto.rename.module.entity");
    }
  }
}