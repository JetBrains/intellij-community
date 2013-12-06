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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Set;

/**
 * @author yole
 */
public class UnmarkRootAction extends MarkRootActionBase {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    RootsSelection selection = getSelection(e);
    Set<JpsModuleSourceRootType<?>> selectedRootTypes = new HashSet<JpsModuleSourceRootType<?>>();
    for (SourceFolder root : selection.mySelectedRoots) {
      selectedRootTypes.add(root.getRootType());
    }

    if (!selectedRootTypes.isEmpty()) {
      String text;
      if (selectedRootTypes.size() == 1) {
        JpsModuleSourceRootType<?> type = selectedRootTypes.iterator().next();
        ModuleSourceRootEditHandler<?> handler = ModuleSourceRootEditHandler.getEditHandler(type);
        text = "Unmark as " + handler.getRootTypeName() + " " + StringUtil.pluralize("Root", selection.mySelectedRoots.size());
      }
      else {
        text = "Unmark Roots";
      }
      e.getPresentation().setText(text);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    return selection.mySelectedDirectories.isEmpty() && !selection.mySelectedRoots.isEmpty();
  }

  protected void modifyRoots(VirtualFile vFile, ContentEntry entry) {
  }
}
