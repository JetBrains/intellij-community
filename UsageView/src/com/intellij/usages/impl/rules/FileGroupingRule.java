package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
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
      UsageInFile usageInFile = (UsageInFile)usage;
      return new FileUsageGroup(usageInFile.getFile());
    }
    
    return null;
  }

  private class FileUsageGroup implements UsageGroup, DataProvider {
    private VirtualFile myFile;
    private String myPresentableName;

    public FileUsageGroup(VirtualFile file) {
      myFile = file;
      myPresentableName = myFile.getPresentableName();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileUsageGroup)) return false;

      final FileUsageGroup fileUsageGroup = (FileUsageGroup)o;

      if (myFile != null ? !myFile.equals(fileUsageGroup.myFile) : fileUsageGroup.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      return (myFile != null ? myFile.hashCode() : 0);
    }

    public Icon getIcon(boolean isOpen) {
      return IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, myProject);
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

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
        return myFile;
      }
      if (DataConstants.PSI_ELEMENT.equals(dataId)) {
        return PsiManager.getInstance(myProject).findFile(myFile);
      }
      return null;
    }
  }
}
