package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInLibrary;
import com.intellij.usages.rules.UsageInModule;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:32:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class ModuleGroupingRule implements UsageGroupingRule {
  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof UsageInModule) {
      UsageInModule usageInModule = (UsageInModule)usage;
      Module module = usageInModule.getModule();
      if (module != null) return new ModuleUsageGroup(module);
    }

    if (usage instanceof UsageInLibrary) {
      UsageInLibrary usageInLibrary = (UsageInLibrary)usage;
      OrderEntry entry = usageInLibrary.getLibraryEntry();
      if (entry != null) return new LibraryUsageGroup(entry);
    }

    return null;
  }

  private static class LibraryUsageGroup implements UsageGroup, DataProvider {
    public static final Icon LIBRARY_ICON = IconLoader.getIcon("/nodes/ppLibOpen.png");

    OrderEntry myEntry;

    public LibraryUsageGroup(OrderEntry entry) {
      myEntry = entry;
    }

    public Icon getIcon(boolean isOpen) {
      return LIBRARY_ICON;
    }

    public String getText(UsageView view) {
      return myEntry.getPresentableName();
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() {
      return true;
    }

    public int compareTo(UsageGroup usageGroup) {
      if (usageGroup instanceof ModuleUsageGroup) return 1;
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public void navigate(boolean requestFocus) {
    }

    public boolean canNavigate() {
      return false;
    }

    public Object getData(String dataId) {
      return null;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LibraryUsageGroup)) return false;

      return myEntry.equals(((LibraryUsageGroup)o).myEntry);
    }

    public int hashCode() {
      return myEntry.hashCode();
    }
  }

  private static class ModuleUsageGroup implements UsageGroup, DataProvider {
    private final Module myModule;

    public ModuleUsageGroup(Module module) {
      myModule = module;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ModuleUsageGroup)) return false;

      final ModuleUsageGroup moduleUsageGroup = (ModuleUsageGroup)o;

      if (myModule != null ? !myModule.equals(moduleUsageGroup.myModule) : moduleUsageGroup.myModule != null) return false;

      return true;
    }

    public int hashCode() {
      return (myModule != null ? myModule.hashCode() : 0);
    }

    public Icon getIcon(boolean isOpen) {
      return myModule.getModuleType().getNodeIcon(isOpen);
    }

    public String getText(UsageView view) {
      return myModule.getName();
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() {
      return !myModule.isDisposed();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
    }

    public boolean canNavigate() {
      return false;
    }

    public int compareTo(UsageGroup o) {
      if (o instanceof LibraryUsageGroup) return -1;
      return o.getText(null).compareTo(getText(null));
    }

    public String toString() {
      return "Module:" + getText(null);
    }

    public Object getData(String dataId) {
      if (DataConstants.MODULE_CONTEXT.equals(dataId)) {
        return myModule;
      }
      return null;
    }    
  }
}
