package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FileRelativeDirMacro2 extends FileRelativeDirMacro {
  public String getName() {
    return "/FileRelativeDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.directory.relative.fwd.slash");
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
