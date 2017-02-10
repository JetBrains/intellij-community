/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author yole
 */
public class UnmarkRootAction extends MarkRootActionBase {
  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @Nullable Module module, @NotNull RootsSelection selection) {
    if (!Registry.is("ide.hide.excluded.files") && !selection.mySelectedExcludeRoots.isEmpty()
        && selection.mySelectedDirectories.isEmpty() && selection.mySelectedRoots.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(true);
      e.getPresentation().setText(LangBundle.message("mark.as.unmark.excluded"));
      return;
    }

    super.doUpdate(e, module, selection);

    String text = getActionText(e, module, selection);
    if (text != null) e.getPresentation().setText(text);
  }

  @Nullable
  protected String getActionText(@NotNull AnActionEvent e, @Nullable Module module, @NotNull RootsSelection selection) {
    Set<ModuleSourceRootEditHandler<?>> selectedRootHandlers = getHandlersForSelectedRoots(selection);

    if (!selectedRootHandlers.isEmpty()) {
      if (selectedRootHandlers.size() == 1) {
        ModuleSourceRootEditHandler<?> handler = selectedRootHandlers.iterator().next();
        return LangBundle.message("mark.as.unmark", 
                                  StringUtil.pluralize(handler.getFullRootTypeName(), selection.mySelectedRoots.size()));
      }
      else {
        return LangBundle.message("mark.as.unmark.several");
      }
    }
    return null;
  }

  @NotNull
  private static Set<ModuleSourceRootEditHandler<?>> getHandlersForSelectedRoots(@NotNull RootsSelection selection) {
    Set<ModuleSourceRootEditHandler<?>> selectedRootHandlers = new HashSet<>();
    for (SourceFolder root : selection.mySelectedRoots) {
      ContainerUtil.addIfNotNull(selectedRootHandlers, ModuleSourceRootEditHandler.getEditHandler(root.getRootType()));
    }
    return selectedRootHandlers;
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    return selection.mySelectedDirectories.isEmpty() && !getHandlersForSelectedRoots(selection).isEmpty();
  }

  protected void modifyRoots(@NotNull VirtualFile file, @NotNull ContentEntry entry) {
    for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
      if (file.equals(excludeFolder.getFile())) {
        entry.removeExcludeFolder(excludeFolder);
        break;
      }
    }
  }
}
