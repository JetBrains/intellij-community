package com.intellij.ide.macro;

import com.intellij.ide.DataAccessors;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class FileRelativePathMacro extends Macro {
  public String getName() {
    return "FileRelativePath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.path.relative");
  }

  public String expand(DataContext dataContext) {
    final VirtualFile baseDir = DataAccessors.PROJECT_BASE_DIR.from(dataContext);
    if (baseDir == null) {
      return null;
    }

    VirtualFile file = DataAccessors.VIRTUAL_FILE.from(dataContext);
    if (file == null) return null;
    return FileUtil.getRelativePath(VfsUtil.virtualToIoFile(baseDir), VfsUtil.virtualToIoFile(file));
  }
}
