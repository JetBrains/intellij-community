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

package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Model for "Go to | File" action
 */
public class GotoFileModel extends ContributorsBasedGotoByModel{
  private final int myMaxSize;
  /** current file types */
  private HashSet<FileType> myFileTypes;

  public GotoFileModel(Project project) {
    super(project, Extensions.getExtensions(ChooseByNameContributor.FILE_EP_NAME));
    myMaxSize = WindowManagerEx.getInstanceEx().getFrame(project).getSize().width;
  }

  /**
   * Set file types
   * @param fileTypes a file types to set 
   */
  public synchronized void setFileTypes(FileType[] fileTypes) {
    // get and set method are called from different threads
    myFileTypes = new HashSet<FileType>(Arrays.asList(fileTypes));
  }

  /**
   * @return get file types
   */
  private synchronized Set<FileType> getFileTypes() {
    // get and set method are called from different threads
    return myFileTypes;
  }

  @Override
  protected boolean acceptItem(final NavigationItem item) {
    if (item instanceof PsiFile) {
      final PsiFile file = (PsiFile)item;
      final Set<FileType> types = getFileTypes();
      // if language substitutors are used, PsiFile.getFileType() can be different from
      // PsiFile.getVirtualFile().getFileType()
      if (types != null) {
        if (types.contains(file.getFileType())) return true;
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null && types.contains(vFile.getFileType())) return true;
        return false;
      }
      return true;
    }
    else {
      return super.acceptItem(item);
    }
  }

  public String getPromptText() {
    return IdeBundle.message("prompt.gotofile.enter.file.name");
  }

  public String getCheckBoxName() {
    return IdeBundle.message("checkbox.include.non.project.files");
  }

  public char getCheckBoxMnemonic() {
    return SystemInfo.isMac?'P':'n';
  }

  public String getNotInMessage() {
    return IdeBundle.message("label.no.non.java.files.found");
  }

  public String getNotFoundMessage() {
    return IdeBundle.message("label.no.files.found");
  }

  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToClass.includeJavaFiles");
  }

  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("GoToClass.includeJavaFiles", Boolean.toString(state));
  }

  public PsiElementListCellRenderer getListCellRenderer() {
    return new GotoFileCellRenderer(myMaxSize);
  }

  @Nullable
  public String getFullName(final Object element) {
    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      return virtualFile != null ? virtualFile.getPath() : null;
    }

    return getElementName(element);
  }

  @NotNull
  public String[] getSeparators() {
    return new String[] {"/", "\\"};
  }

  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.class";
  }
}