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
package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 5:20:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class FileGroupingRule implements UsageGroupingRule {
  private Project myProject;

  public FileGroupingRule(Project project) {
    myProject = project;
  }

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof UsageInFile) {
      return new FileUsageGroup(myProject, ((UsageInFile)usage).getFile());
    }
    return null;
  }

  protected static class FileUsageGroup implements UsageGroup, DataProvider {
    private final Project myProject;
    private final VirtualFile myFile;
    private final String myPresentableName;
    private Icon myIcon;

    public FileUsageGroup(Project project, VirtualFile file) {
      myProject = project;
      myFile = file;
      myPresentableName = myFile.getPresentableName();
      update();
    }

    private Icon getIconImpl() {
      return IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, myProject);
    }

    public void update() {
      if (isValid()) {
        myIcon = getIconImpl();
      }
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileUsageGroup)) return false;

      final FileUsageGroup fileUsageGroup = (FileUsageGroup)o;

      if (myFile != null ? !myFile.equals(fileUsageGroup.myFile) : fileUsageGroup.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      return myFile != null ? myFile.hashCode() : 0;
    }

    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    public String getText(UsageView view) {
      return myPresentableName;
    }

    public FileStatus getFileStatus() {
      return isValid() ? FileStatusManager.getInstance(myProject).getStatus(myFile) : null;
    }

    public boolean isValid() {
      return myFile.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      FileEditorManager.getInstance(myProject).openFile(myFile, focus);
    }

    public boolean canNavigate() {
      return true;
    }

    public boolean canNavigateToSource() {
      return canNavigate();
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
        return myFile != null && myFile.isValid()? myFile : null;
      }
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return myFile != null && myFile.isValid()? getPsiFile() : null;
      }
      return null;
    }

    public PsiFile getPsiFile() {
      return PsiManager.getInstance(myProject).findFile(myFile);
    }
  }
}
