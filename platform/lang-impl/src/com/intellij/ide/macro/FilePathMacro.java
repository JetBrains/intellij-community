package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;

public final class FilePathMacro extends Macro {
  public String getName() {
    return "FilePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.path");
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;
    return getPath(file);
  }
}
