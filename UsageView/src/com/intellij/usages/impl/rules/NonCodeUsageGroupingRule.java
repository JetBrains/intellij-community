package com.intellij.usages.impl.rules;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 23, 2004
 * Time: 2:41:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class NonCodeUsageGroupingRule implements UsageGroupingRule {
  private static class CodeUsageGroup implements UsageGroup {
    public static final UsageGroup INSTANCE = new CodeUsageGroup();
    private CodeUsageGroup() {}

    public String getText(UsageView view) {
      return view == null ? "Code usages" : view.getPresentation().getCodeUsagesString();
    }

    public String toString() { return "CodeUsages"; }
    public Icon getIcon(boolean isOpen) { return null; }
    public FileStatus getFileStatus() { return null; }
    public boolean isValid() { return true; }
    public int compareTo(UsageGroup usageGroup) { return usageGroup == this ? 0 : 1; }
    public void navigate(boolean requestFocus) { }
    public boolean canNavigate() { return false; }
  }

  private static class NonCodeUsageGroup implements UsageGroup {
    public static final UsageGroup INSTANCE = new NonCodeUsageGroup();
    private NonCodeUsageGroup() {}

    public String getText(UsageView view) {
      return view == null ? "Code usages" : view.getPresentation().getNonCodeUsagesString();
    }

    public String toString() { return "NonCodeUsages"; }
    public Icon getIcon(boolean isOpen) { return null; }
    public FileStatus getFileStatus() { return null; }
    public boolean isValid() { return true; }
    public int compareTo(UsageGroup usageGroup) { return usageGroup == this ? 0 : -1; }
    public void navigate(boolean requestFocus) { }
    public boolean canNavigate() { return false; }
  }

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      return ((PsiElementUsage)usage).isNonCodeUsage() ? NonCodeUsageGroup.INSTANCE : CodeUsageGroup.INSTANCE;
    }
    return null;
  }
}
