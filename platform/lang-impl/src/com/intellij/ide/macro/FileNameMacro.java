package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;

public class FileNameMacro extends Macro {
  public String getName() {
    return "FileName";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.name");
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;
    return file.getName();
  }
}
