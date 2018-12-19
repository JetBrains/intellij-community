/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.ui.ProductIcons;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Intended for use in actions related to opening or importing existing projects.
 * <strong>Due to a high I/O impact SHOULD NOT be used in any other cases.</strong>
 */
public class OpenProjectFileChooserDescriptor extends FileChooserDescriptor {
  private static final boolean ourCanInspectDirs = SystemProperties.getBooleanProperty("idea.chooser.lookup.for.project.dirs", true);

  public OpenProjectFileChooserDescriptor(boolean chooseFiles) {
    this(chooseFiles, chooseFiles);
  }

  public OpenProjectFileChooserDescriptor(boolean chooseFiles, boolean chooseJars) {
    super(chooseFiles, true, chooseJars, chooseJars, false, false);
    setHideIgnored(false);
  }

  @Override
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || isProjectFile(file));
  }

  @Override
  public boolean isFileSelectable(VirtualFile file) {
    return isProjectDirectory(file) || isProjectFile(file);
  }

  @Override
  public Icon getIcon(VirtualFile file) {
    if (canInspectDirectory(file)) {
      if (isIprFile(file) || isIdeaDirectory(file)) {
        return dressIcon(file, ProductIcons.getInstance().getProjectNodeIcon());
      }
      Icon icon = getImporterIcon(file);
      if (icon != null) {
        return dressIcon(file, icon);
      }
    }
    return super.getIcon(file);
  }

  private static boolean canInspectDirectory(VirtualFile file) {
    VirtualFile home = VfsUtil.getUserHomeDir();
    if (home == null || VfsUtilCore.isAncestor(file, home, false)) {
      return false;
    }
    if (VfsUtilCore.isAncestor(home, file, true)) {
      return true;
    }
    if (SystemInfo.isUnix && file.isInLocalFileSystem()) {
      VirtualFile parent = file.getParent();
      if (parent != null && parent.getParent() == null) {
        return false;
      }
    }
    return ourCanInspectDirs;
  }

  private static Icon getImporterIcon(VirtualFile file) {
    ProjectOpenProcessor provider = ProjectOpenProcessor.getImportProvider(file);
    if (provider != null) {
      return file.isDirectory() && provider.lookForProjectsInDirectory() ? ProductIcons.getInstance().getProjectNodeIcon()
                                                                         : provider.getIcon(file);
    }
    return null;
  }

  public static boolean isProjectFile(@NotNull VirtualFile file) {
    return !file.isDirectory() && file.isValid() && (isIprFile(file) || hasImportProvider(file));
  }

  private static boolean isProjectDirectory(@NotNull VirtualFile file) {
    return file.isDirectory() && file.isValid() && (isIdeaDirectory(file) || hasImportProvider(file));
  }

  private static boolean isIprFile(VirtualFile file) {
    return ProjectFileType.DEFAULT_EXTENSION.equalsIgnoreCase(file.getExtension());
  }

  private static boolean isIdeaDirectory(VirtualFile file) {
    return ProjectKt.getProjectStoreDirectory(file) != null;
  }

  private static boolean hasImportProvider(VirtualFile file) {
    return ProjectOpenProcessor.getImportProvider(file) != null;
  }
}
