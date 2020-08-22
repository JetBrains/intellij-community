// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PackageGroupingRule extends DirectoryGroupingRule {
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

  private final class PackageGroup implements UsageGroup, TypeSafeDataProvider {
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
    public void calcData(@NotNull final DataKey key, @NotNull final DataSink sink) {
      if (!isValid()) return;
      if (CommonDataKeys.PSI_ELEMENT == key) {
        sink.put(CommonDataKeys.PSI_ELEMENT, myPackage);
      }
    }
  }
}
