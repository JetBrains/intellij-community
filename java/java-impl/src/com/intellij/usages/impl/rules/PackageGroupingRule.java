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
package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class PackageGroupingRule extends DirectoryGroupingRule {
  public PackageGroupingRule(Project project) {
    super(project);
  }

  @Override
  protected UsageGroup getGroupForFile(@NotNull final VirtualFile dir) {
    PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(dir);
    if (psiDirectory != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      if (aPackage != null) return new PackageGroup(aPackage);
    }
    return super.getGroupForFile(dir);
  }

  @Override
  public String getActionTitle() {
    return UsageViewBundle.message("action.group.by.package");
  }

  private class PackageGroup implements UsageGroup, TypeSafeDataProvider {
    private final PsiPackage myPackage;
    private Icon myIcon;

    private PackageGroup(PsiPackage aPackage) {
      myPackage = aPackage;
      update();
    }

    @Override
    public void update() {
      if (isValid()) {
        myIcon = myPackage.getIcon(0);
      }
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return myPackage.getQualifiedName();
    }

    @Override
    public FileStatus getFileStatus() {
      if (!isValid()) return null;
      PsiDirectory[] dirs = myPackage.getDirectories();
      return dirs.length == 1 ? FileStatusManager.getInstance(myProject).getStatus(dirs[0].getVirtualFile()) : null;
    }

    @Override
    public boolean isValid() {
      return myPackage.isValid();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      myPackage.navigate(focus);
    }

    @Override
    public boolean canNavigate() {
      return myPackage.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      return getText(null).compareToIgnoreCase(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PackageGroup)) return false;

      return myPackage.equals(((PackageGroup)o).myPackage);
    }

    public int hashCode() {
      return myPackage.hashCode();
    }

    @Override
    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (CommonDataKeys.PSI_ELEMENT == key) {
        sink.put(CommonDataKeys.PSI_ELEMENT, myPackage);
      }
    }
  }
}
