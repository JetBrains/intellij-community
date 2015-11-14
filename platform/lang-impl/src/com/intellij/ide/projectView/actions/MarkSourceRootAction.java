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
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Locale;

/**
 * @author nik
 */
public class MarkSourceRootAction extends MarkRootActionBase {
  private static final Logger LOG = Logger.getInstance(MarkSourceRootAction.class);
  private final JpsModuleSourceRootType<?> myRootType;

  public MarkSourceRootAction(@NotNull JpsModuleSourceRootType<?> type) {
    myRootType = type;
    Presentation presentation = getTemplatePresentation();
    ModuleSourceRootEditHandler<?> editHandler = ModuleSourceRootEditHandler.getEditHandler(type);
    LOG.assertTrue(editHandler != null);
    presentation.setIcon(editHandler.getRootIcon());
    presentation.setText(editHandler.getFullRootTypeName());
    presentation.setDescription(ProjectBundle.message("module.toggle.sources.action.description", 
                                                      editHandler.getFullRootTypeName().toLowerCase(Locale.getDefault())));
  }

  protected void modifyRoots(@NotNull VirtualFile vFile, @NotNull ContentEntry entry) {
    entry.addSourceFolder(vFile, myRootType);
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    if (!ModuleType.get(module).isSupportedRootType(myRootType) || selection.myHaveSelectedFilesUnderSourceRoots
        || ModuleSourceRootEditHandler.getEditHandler(myRootType) == null) {
      return false;
    }

    if (!selection.mySelectedDirectories.isEmpty()) {
      return true;
    }

    for (SourceFolder root : selection.mySelectedRoots) {
      if (!myRootType.equals(root.getRootType())) {
        return true;
      }
    }
    return false;
  }
}
