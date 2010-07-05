/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenProjectFileChooserDescriptor extends FileChooserDescriptor {
  private final Icon myProjectIcon = IconLoader.getIcon(ApplicationInfoImpl.getInstanceEx().getSmallIconUrl());

  public OpenProjectFileChooserDescriptor(final boolean chooseFiles) {
    super(chooseFiles, true, false, false, false, false);
  }

  public boolean isFileSelectable(final VirtualFile file) {
    return isProjectDirectory(file) || isProjectFile(file);
  }

  public Icon getOpenIcon(final VirtualFile virtualFile) {
    if (isProjectDirectory(virtualFile)) return myProjectIcon;
    final Icon icon = getImporterIcon(virtualFile, true);
    if(icon!=null){
      return icon;
    }
    return super.getOpenIcon(virtualFile);
  }

  public Icon getClosedIcon(final VirtualFile virtualFile) {
    if (isProjectDirectory(virtualFile)) return myProjectIcon;
    final Icon icon = getImporterIcon(virtualFile, false);
    if(icon!=null){
      return icon;
    }
    return super.getClosedIcon(virtualFile);
  }

  @Nullable
  public static Icon getImporterIcon(final VirtualFile virtualFile, final boolean open) {
    final ProjectOpenProcessor provider = ProjectOpenProcessor.getImportProvider(virtualFile);
    if(provider!=null) {
      return provider.getIcon();
    }
    return null;
  }

  public boolean isFileVisible(final VirtualFile file, final boolean showHiddenFiles) {
    if (!showHiddenFiles && FileElement.isFileHidden(file)) return false;
    return isProjectFile(file) || super.isFileVisible(file, showHiddenFiles) && file.isDirectory();
  }

  private static boolean isProjectFile(final VirtualFile file) {
    if ((!file.isDirectory() && file.getName().toLowerCase().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION))) {
      return true;
    }
    final ProjectOpenProcessor importProvider = ProjectOpenProcessor.getImportProvider(file);
    if (importProvider != null && !(importProvider instanceof PlatformProjectOpenProcessor)) {
      return true;
    }
    return false;
  }

  private static boolean isProjectDirectory(final VirtualFile virtualFile) {
    // the root directory of any drive is never an IDEA project
    if (virtualFile.getParent() == null) return false;
    if (virtualFile.isDirectory() && virtualFile.isValid()) {
      final VirtualFile[] children = virtualFile.getChildren();
      if (children == null) return false;
      for (VirtualFile file : children) {
        if (file.getName().equals(Project.DIRECTORY_STORE_FOLDER)) return true;
        if (isProjectFile(file)) return true;
      }
    }
    return false;
  }
}
