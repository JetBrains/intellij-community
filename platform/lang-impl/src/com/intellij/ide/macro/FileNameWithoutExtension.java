package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;


public final class FileNameWithoutExtension extends FileNameMacro {
  public String getName() {
    return "FileNameWithoutExtension";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.name.without.extension");
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (file == null) return null;
    return file.getNameWithoutExtension();
  }
}