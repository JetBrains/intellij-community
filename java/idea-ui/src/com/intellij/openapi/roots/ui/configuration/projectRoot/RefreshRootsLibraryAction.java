// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.JavaUiBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class RefreshRootsLibraryAction extends DumbAwareAction {
  @NotNull private final BaseLibrariesConfigurable myConfigurable;

  RefreshRootsLibraryAction(@NotNull BaseLibrariesConfigurable configurable) {
    super(JavaUiBundle.message("refresh.library.roots.action.name"));
    myConfigurable = configurable;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Library library = getLibrary();
    if (library == null) return;
    List<String> allUrls = new ArrayList<>();
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      Collections.addAll(allUrls, library.getUrls(rootType));
    }

    new Task.Backgroundable(e.getProject(), LangBundle.message("progress.title.refreshing")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        for (int i = 0; i < allUrls.size(); i++) {
          String url = allUrls.get(i);
          VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
          indicator.setFraction(((double) i) / allUrls.size());
        }
      }
    }.queue();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Library library = getLibrary();
    e.getPresentation().setEnabledAndVisible(library != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Nullable
  private Library getLibrary() {
    ProjectStructureElement element = myConfigurable.getSelectedElement();
    return element instanceof LibraryProjectStructureElement ? ((LibraryProjectStructureElement)element).getLibrary() : null;
  }
}
