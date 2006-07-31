/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public class FileChooserDescriptor implements Cloneable{
  private final boolean myChooseFiles;
  private final boolean myChooseFolders;
  private final boolean myChooseJars;
  private final boolean myChooseJarsAsFiles;
  private final boolean myChooseJarContents;
  private final boolean myChooseMultiple;

  private String myTitle = UIBundle.message("file.chooser.default.title");
  private String myDescription;

  private boolean myHideIgnored = true;
  private List<VirtualFile> myRoots = new ArrayList<VirtualFile>();
  private boolean myShowFileSystemRoots = true;
  private boolean myIsTreeRootVisible = false;
  private Module myContextModule = null;

  /**
   * @param chooseFiles controls whether files can be chosen
   * @param chooseFolders controls whether folders can be chosen
   * @param chooseJars
   * @param chooseJarsAsFiles controls whether the jar files will be returned as files or as folders
   * @param chooseJarContents controls whether user can choose jar files and their contents
   * @param chooseMultiple
   */ 
  public FileChooserDescriptor(
    boolean chooseFiles, 
    boolean chooseFolders, 
    boolean chooseJars, 
    boolean chooseJarsAsFiles, 
    boolean chooseJarContents, 
    boolean chooseMultiple
  ){
    myChooseFiles = chooseFiles;
    myChooseFolders = chooseFolders;
    myChooseJars = chooseJars;
    myChooseJarsAsFiles = chooseJarsAsFiles;
    myChooseJarContents = chooseJarContents;
    myChooseMultiple = chooseMultiple;
  }

  public final String getTitle() {
    return myTitle;
  }

  public final void setTitle(String title) {
    myTitle = title;
  }

  public boolean isShowFileSystemRoots() {
    return myShowFileSystemRoots;
  }

  public void setShowFileSystemRoots(boolean showFileSystemRoots) {
    myShowFileSystemRoots = showFileSystemRoots;
  }

  public final String getDescription() {
    return myDescription;
  }

  public final void setDescription(String description) {
    myDescription = description;
  }

  public final boolean isChooseJarContents() {
    return myChooseJarContents;
  }

  public boolean isChooseFiles() {
    return myChooseFiles;
  }

  /**
   * If true, the user will be able to choose multiple files.
   */
  public final boolean getChooseMultiple() {
    return myChooseMultiple;
  }

  /**
   * Defines whether file can be chosen or not 
   */ 
  public boolean isFileSelectable(VirtualFile file) {
    if (file.isDirectory() && myChooseFolders) return true;
    if (acceptAsJarFile(file)) return true;
    if (acceptAsGeneralFile(file)) return true;
    return false;
  }

  /**
   * Defines whether file is visible in the tree
   */ 
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    if (!file.isDirectory()) {
      if (FileElement.isArchive(file)) {
        if (!myChooseJars) {
          return false;
        }
      }
      else {
        if (!myChooseFiles) {
          return false;
        }
      }
    }

    // do not include ignored files
    if (myHideIgnored && FileTypeManager.getInstance().isFileIgnored(file.getName())) {
      return false;
    }

    // do not include hidden files
    if (!showHiddenFiles) {
      if (FileElement.isFileHidden(file)) {
        return false;
      }
    }
    
    return true;
  }

  public Icon getOpenIcon(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return Icons.DIRECTORY_OPEN_ICON;
    }
    // deliberately pass project null: isJavaSourceFile() and excluded from compile information is unavailable for template project
    return IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, null);
  }
  public Icon getClosedIcon(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return Icons.DIRECTORY_CLOSED_ICON;
    }
    return IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, null);
  }

  public String getName(VirtualFile virtualFile) {
    return virtualFile.getPath();
  }


  @Nullable
  public String getComment(VirtualFile virtualFile) {
    return null;
  }

  /**
   * the method is called upon pressing Ok in the FileChooserDialog
   * Override the method in order to customize validation of user input
   * @param files - selected files to be checked
   * @throws Exception if the the files cannot be accepted
   */
  public void validateSelectedFiles(VirtualFile[] files) throws Exception {
  }

  private boolean acceptAsGeneralFile(VirtualFile file) {
    return !file.isDirectory() && myChooseFiles;
  }

  private boolean acceptAsJarFile(VirtualFile file) {
    return myChooseJars && FileElement.isArchive(file);
  }

  public final VirtualFile getFileToSelect(VirtualFile file) {
    if (file.isDirectory() && myChooseFolders) {
      return file;
    }
    boolean isJar = FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.ARCHIVE;
    if (!isJar) {
      return acceptAsGeneralFile(file) ? file : null;
    }
    if (myChooseJarsAsFiles) {
      return file;
    }
    if (!acceptAsJarFile(file)) {
      return null;
    }
    String path = file.getPath();
    return JarFileSystem.getInstance().findFileByPath(path + JarFileSystem.JAR_SEPARATOR);
  }

  public final void setHideIgnored(boolean hideIgnored) { myHideIgnored = hideIgnored; }

  public final List<VirtualFile> getRoots() {
    return myRoots;
  }

  public final void setRoot(VirtualFile root) {
    myRoots.clear();
    addRoot(root);
  }
  public void addRoot(VirtualFile root) {
    myRoots.add(root);
  }

  public boolean isTreeRootVisible() {
    return myIsTreeRootVisible;
  }

  public void setIsTreeRootVisible(boolean isTreeRootVisible) {
    myIsTreeRootVisible = isTreeRootVisible;
  }

  public final Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isChooseFolders() {
    return myChooseFolders;
  }

  public boolean isChooseJars() {
    return myChooseJars;
  }

  public boolean isChooseJarsAsFiles() {
    return myChooseJarsAsFiles;
  }

  public boolean isChooseMultiple() {
    return myChooseMultiple;
  }

  public boolean isHideIgnored() {
    return myHideIgnored;
  }

  public Module getContextModule() {
    return myContextModule;
  }

  public void setContextModule(final Module contextModule) {
    myContextModule = contextModule;
  }
}