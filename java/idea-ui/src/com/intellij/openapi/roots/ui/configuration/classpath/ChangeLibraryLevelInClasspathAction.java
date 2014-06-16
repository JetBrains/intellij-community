/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.AnActionEvent;
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

/**
 * @author nik
 */
class ChangeLibraryLevelInClasspathAction extends ChangeLibraryLevelActionBase {
  private final ClasspathPanel myPanel;

  public ChangeLibraryLevelInClasspathAction(@NotNull ClasspathPanel panel, final @NotNull String targetTableName, @NotNull String targetTableLevel) {
    super(panel.getProject(), targetTableName, targetTableLevel, targetTableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL));
    myPanel = panel;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    if (!(entry instanceof LibraryOrderEntry)) return;
    LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
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
    if (entry instanceof LibraryOrderEntry) {
      final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
      if (libraryOrderEntry.getLibrary() != null) {
        boolean isFromModuleLibrary = libraryOrderEntry.isModuleLevel();
        boolean isToModuleLibrary = isConvertingToModuleLibrary();
        enabled = isFromModuleLibrary != isToModuleLibrary;
      }
    }
    return enabled;
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
  @Nullable
  protected VirtualFile getBaseDir() {
    if (isConvertingToModuleLibrary()) {
      final VirtualFile[] roots = myPanel.getRootModel().getContentRoots();
      if (roots.length > 0) {
        return roots[0];
      }
      final VirtualFile moduleFile = myPanel.getRootModel().getModule().getModuleFile();
      if (moduleFile != null) {
        return moduleFile.getParent();
      }
    }
    return super.getBaseDir();
  }
}
