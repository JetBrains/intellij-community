// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class ChangeLibraryLevelInClasspathAction extends ChangeLibraryLevelActionBase {
  private final ClasspathPanel myPanel;

  ChangeLibraryLevelInClasspathAction(@NotNull ClasspathPanel panel, final @NotNull String targetTableName, @NotNull String targetTableLevel) {
    super(panel.getProject(), targetTableName, targetTableLevel, targetTableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL));
    myPanel = panel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    if (!(entry instanceof LibraryOrderEntry libraryEntry)) return;
    final LibraryEx library = (LibraryEx)libraryEntry.getLibrary();
    if (library == null) return;

    final Library copied = doCopy(library);
    if (copied == null) return;

    if (!isConvertingToModuleLibrary()) {
      OrderEntryUtil.replaceLibrary(myPanel.getRootModel(), library, copied);
    }
    else {
      OrderEntryUtil.replaceLibraryEntryByAdded(myPanel.getRootModel(), libraryEntry);
    }
  }

  @Override
  protected boolean isEnabled() {
    final OrderEntry entry = myPanel.getSelectedEntry();
    boolean enabled = false;
    if (entry instanceof LibraryOrderEntry libraryOrderEntry && libraryOrderEntry.getLibrary() != null) {
      boolean isFromModuleLibrary = libraryOrderEntry.isModuleLevel();
      boolean isToModuleLibrary = isConvertingToModuleLibrary();
      enabled = isFromModuleLibrary != isToModuleLibrary;
    }
    return enabled;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected LibraryTableModifiableModelProvider getModifiableTableModelProvider() {
    return myPanel.getModifiableModelProvider(myTargetTableLevel);
  }

  @Override
  protected JComponent getParentComponent() {
    return myPanel.getComponent();
  }

  @Override
  protected @Nullable VirtualFile getBaseDir() {
    if (isConvertingToModuleLibrary()) {
      final VirtualFile[] roots = myPanel.getRootModel().getContentRoots();
      if (roots.length > 0) {
        return roots[0];
      }
      return ProjectUtil.guessModuleDir(myPanel.getRootModel().getModule());
    }
    return super.getBaseDir();
  }
}
