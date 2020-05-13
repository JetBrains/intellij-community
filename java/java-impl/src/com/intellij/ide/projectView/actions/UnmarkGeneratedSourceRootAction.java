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

import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Locale;

public class UnmarkGeneratedSourceRootAction extends MarkRootActionBase {
  public UnmarkGeneratedSourceRootAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.Modules.SourceRoot);

    ModuleSourceRootEditHandler<JavaSourceRootProperties> handler = ModuleSourceRootEditHandler.getEditHandler(JavaSourceRootType.SOURCE);
    if (handler == null) return;

    String typeName = handler.getFullRootTypeName();
    presentation.setText(JavaBundle.message("action.text.unmark.generated.0", typeName));
    presentation.setDescription(JavaBundle.message("action.description.mark.directory.as.an.ordinary.0", typeName.toLowerCase(Locale.getDefault())));
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    for (SourceFolder root : selection.mySelectedRoots) {
      JavaSourceRootProperties properties = root.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
      if (properties != null && properties.isForGeneratedSources()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void modifyRoots(@NotNull VirtualFile vFile, @NotNull ContentEntry entry) {
    entry.addSourceFolder(vFile, JavaSourceRootType.SOURCE);
  }
}
