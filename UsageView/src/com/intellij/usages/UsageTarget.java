package com.intellij.usages;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:15:46 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageTarget extends NavigationItem {
  /**
   * Should display a usage dialog, open usage view and look for usages of itself
   */
  void findUsages();

  /**
   * Should look for usages in one specific editor. This typicaly shows other kind of dialog and doesn't
   * result in usage view display.
   */
  void findUsagesInEditor(FileEditor editor);

  boolean isValid();
  boolean isReadOnly();

  /**
   * @return the files this usage target is in. Might be null is usage target is not file-based
   */
  VirtualFile[] getFiles();

}
