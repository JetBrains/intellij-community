// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

final class DetachLibraryDeleteProvider implements DeleteProvider {

  private final Project myProject;
  private final LibraryOrderEntry myOrderEntry;

  DetachLibraryDeleteProvider(@NotNull Project project, @NotNull LibraryOrderEntry orderEntry) {
    myProject = project;
    myOrderEntry = orderEntry;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    final Module module = myOrderEntry.getOwnerModule();
    String message = IdeBundle.message("detach.library.from.module", myOrderEntry.getPresentableName(), module.getName());
    String title = IdeBundle.message("detach.library");
    int ret = Messages.showOkCancelDialog(myProject, message, title, Messages.getQuestionIcon());
    if (ret != Messages.OK) return;
    CommandProcessor.getInstance().executeCommand(module.getProject(), () -> {
      final Runnable action = () -> {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        OrderEntry[] orderEntries = rootManager.getOrderEntries();
        ModifiableRootModel model = rootManager.getModifiableModel();
        OrderEntry[] modifiableEntries = model.getOrderEntries();
        for (int i = 0; i < orderEntries.length; i++) {
          OrderEntry entry = orderEntries[i];
          if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() == myOrderEntry.getLibrary()) {
            model.removeOrderEntry(modifiableEntries[i]);
          }
        }
        model.commit();
      };
      ApplicationManager.getApplication().runWriteAction(action);
    }, title, null);
  }
}
