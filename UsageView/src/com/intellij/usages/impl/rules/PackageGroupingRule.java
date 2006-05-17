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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.Icons;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 12:18:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class PackageGroupingRule implements UsageGroupingRule {
  private Project myProject;

  public PackageGroupingRule(Project project) {
    myProject = project;
  }

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof UsageInFile) {
      UsageInFile usageInFile = (UsageInFile)usage;
      VirtualFile file = usageInFile.getFile();
      if (file != null) {
        VirtualFile dir = file.getParent();
        if (dir == null) return null;
        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(dir);
        PsiPackage aPackage = psiDirectory.getPackage();
        if (aPackage != null) return new PackageGroup(aPackage);
        return new DirectoryGroup(dir);
      }
    }
    return null;
  }

  private class PackageGroup implements UsageGroup, DataProvider {
    private PsiPackage myPackage;
    private Icon myOpenIcon;
    private Icon myClosedIcon;

    public PackageGroup(PsiPackage aPackage) {
      myPackage = aPackage;
      update();
    }

    public void update() {
      if (isValid()) {
        myOpenIcon = myPackage.getIcon(Iconable.ICON_FLAG_OPEN);
        myClosedIcon = myPackage.getIcon(Iconable.ICON_FLAG_CLOSED);
      }
    }

    public Icon getIcon(boolean isOpen) {
      return isOpen? myOpenIcon: myClosedIcon;
    }

    public String getText(UsageView view) {
      return myPackage.getQualifiedName();
    }

    public FileStatus getFileStatus() {
      if (!isValid()) return null;
      PsiDirectory[] dirs = myPackage.getDirectories();
      return dirs.length == 1 ? FileStatusManager.getInstance(myProject).getStatus(dirs[0].getVirtualFile()) : null;
    }

    public boolean isValid() {
      return myPackage.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      myPackage.navigate(focus);
    }

    public boolean canNavigate() {
      return myPackage.canNavigate();
    }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PackageGroup)) return false;

      return myPackage.equals(((PackageGroup)o).myPackage);
    }

    public int hashCode() {
      return myPackage.hashCode();
    }

    public Object getData(String dataId) {
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return myPackage;
      }
      return null;
    }
  }

  private class DirectoryGroup implements UsageGroup, DataProvider {
    private VirtualFile myDir;

    public void update() {
    }

    public DirectoryGroup(VirtualFile dir) {
      myDir = dir;
    }

    public Icon getIcon(boolean isOpen) {
      return isOpen ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
    }

    public String getText(UsageView view) {
      return myDir.getPresentableUrl();
    }

    public FileStatus getFileStatus() {
      return isValid() ? FileStatusManager.getInstance(myProject).getStatus(myDir) : null;
    }

    public boolean isValid() {
      return myDir.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      final PsiDirectory directory = getDirectory();
      if (directory != null && directory.canNavigate()) {
        directory.navigate(focus);
      }
    }

    private PsiDirectory getDirectory() {
      return PsiManager.getInstance(myProject).findDirectory(myDir);
    }
    public boolean canNavigate() {
      final PsiDirectory directory = getDirectory();
      return directory != null && directory.canNavigate();
    }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DirectoryGroup)) return false;
      return myDir.equals(((DirectoryGroup)o).myDir);
    }

    public int hashCode() {
      return myDir.hashCode();
    }

    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
        return myDir;
      }
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return PsiManager.getInstance(myProject).findDirectory(myDir);
      }

      return null;
    }
  }
}
