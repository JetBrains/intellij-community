/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class FileChooserDescriptorBuilder {
  private boolean myChooseFiles;
  private boolean myChooseFolders;
  private boolean myChooseJars;
  private boolean myChooseJarsAsFiles;
  private boolean myChooseJarContents;
  private boolean myChooseMultiple;

  private String myTitle = null;
  private String myDescription = null;
  private Boolean myShowFileSystemRoots = null;

  private Boolean myHideIgnored = null;
  private Boolean myTreeRootVisible = null;
  
  private List<VirtualFile> myRoots = null;
  
  

  public FileChooserDescriptorBuilder(boolean chooseFiles,
                               boolean chooseFolders,
                               boolean chooseJars,
                               boolean chooseJarsAsFiles,
                               boolean chooseJarContents,
                               boolean chooseMultiple) {
    myChooseFiles = chooseFiles;
    myChooseFolders = chooseFolders;
    myChooseJars = chooseJars;
    myChooseJarsAsFiles = chooseJarsAsFiles;
    myChooseJarContents = chooseJarContents;
    myChooseMultiple = chooseMultiple;
  }
  
  
  public static FileChooserDescriptorBuilder onlyFiles() {
    return new FileChooserDescriptorBuilder(true, false, false, false, false, false);
  }
  
  public static FileChooserDescriptorBuilder filesAndFolders() {
    return new FileChooserDescriptorBuilder(true, true, false, false, false, false);
  }
  
  public FileChooserDescriptorBuilder chooseMultiple() {
    myChooseMultiple = true;
    return this;
  }

  public FileChooserDescriptorBuilder chooseJars(boolean chooseJars, boolean chooseJarsAsFiles, boolean chooseJarContents) {
    myChooseJars = chooseJars;
    myChooseJarsAsFiles = chooseJarsAsFiles;
    myChooseJarContents = chooseJarContents;
    return this;
  }
  
  public FileChooserDescriptorBuilder withTitle(@NotNull String title) {
    myTitle = title;
    return this;
  }
  
  public FileChooserDescriptorBuilder withDescription(@NotNull String description) {
    myDescription = description;
    return this;
  }
  
  public FileChooserDescriptorBuilder showFileSystemRoots(boolean showFileSystemRoots) {
    myShowFileSystemRoots = showFileSystemRoots;
    return this;
  }
  
  public FileChooserDescriptorBuilder hideIgnored(boolean hideIgnored) {
    myHideIgnored = hideIgnored;
    return this;
  }
  
  public FileChooserDescriptorBuilder withTreeRootVisible(boolean isTreeRootVisible) {
    myTreeRootVisible = isTreeRootVisible;
    return this;
  }
  
  public FileChooserDescriptorBuilder withRoots(List<VirtualFile> roots) {
    myRoots = roots;
    return this;
  }
  
  
  public FileChooserDescriptor build() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(myChooseFiles, myChooseFolders, myChooseJars, myChooseJarsAsFiles, myChooseJarContents, myChooseMultiple);
    
    if (myTitle != null) {
      descriptor.setTitle(myTitle);
    }
    
    if (myDescription != null) {
      descriptor.setDescription(myDescription);
    }
    
    if (myShowFileSystemRoots != null) {
      descriptor.setShowFileSystemRoots(myShowFileSystemRoots);
    }
    
    if (myHideIgnored != null) {
      descriptor.setHideIgnored(myHideIgnored);
    }
    
    if (myTreeRootVisible != null) {
      descriptor.setIsTreeRootVisible(myTreeRootVisible);
    }
    
    if (myRoots != null) {
      descriptor.setRoots(myRoots);
    }
    
    return descriptor;
  }
}
