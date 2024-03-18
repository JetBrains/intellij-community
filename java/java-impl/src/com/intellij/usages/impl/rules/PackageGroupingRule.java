// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.usages.UsageGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PackageGroupingRule extends DirectoryGroupingRule {
  public PackageGroupingRule(@NotNull Project project) {
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

  private Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull String getGroupingActionId() {
    return "UsageGrouping.Package";
  }

  private final class PackageGroup implements UsageGroup, DataProvider {
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
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
      return myPackage.getQualifiedName();
    }

    @Override
    public FileStatus getFileStatus() {
      if (!isValid()) return null;
      PsiDirectory[] dirs = myPackage.getDirectories();
      return dirs.length == 1 ? FileStatusManager.getInstance(getProject()).getStatus(dirs[0].getVirtualFile()) : null;
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
    public int compareTo(@NotNull UsageGroup usageGroup) {
      return getPresentableGroupText().compareToIgnoreCase(usageGroup.getPresentableGroupText());
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
    public @Nullable Object getData(@NotNull String dataId) {
      if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
        return (DataProvider)slowId -> getSlowData(slowId);
      }
      return null;
    }

    private @Nullable Object getSlowData(@NotNull String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return myPackage;
      }
      return null;
    }
  }
}
