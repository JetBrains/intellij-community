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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/**
 * @author nik
 */
public class MarkGeneratedSourceRootAction extends MarkRootActionBase {
  public MarkGeneratedSourceRootAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.Modules.GeneratedSourceRoot);
    presentation.setText("Generated Sources Root");
    presentation.setDescription("Mark directory as a source root for generated files");
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    if (!(ModuleType.get(module) instanceof JavaModuleType)) return false;

    if (selection.myHaveSelectedFilesUnderSourceRoots) {
      return false;
    }

    if (!selection.mySelectedDirectories.isEmpty()) {
      return true;
    }

    for (SourceFolder root : selection.mySelectedRoots) {
      JavaSourceRootProperties properties = root.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
      if (properties != null && !properties.isForGeneratedSources()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void modifyRoots(VirtualFile vFile, ContentEntry entry) {
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    entry.addSourceFolder(vFile, JavaSourceRootType.SOURCE, properties);
  }
}
