package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FileDirRelativeToSourcepathMacro2 extends FileDirRelativeToSourcepathMacro {
  public String getName() {
    return "/FileDirRelativeToSourcepath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.dir.relative.to.sourcepath.root.fwd.slash");
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
