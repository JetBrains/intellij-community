package com.intellij.ide.util;

import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.openapi.actionSystem.Shortcut;

/**
 * @author yole
 */
public interface FileStructureFilter extends Filter {
  String getCheckBoxText();

  Shortcut[] getShortcut();
}
