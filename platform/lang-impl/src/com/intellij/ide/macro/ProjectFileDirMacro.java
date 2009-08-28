package com.intellij.ide.macro;

import com.intellij.ide.DataAccessors;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public final class ProjectFileDirMacro extends Macro {
  public String getName() {
    return "ProjectFileDir";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.file.directory");
  }

  @Nullable
  public String expand(DataContext dataContext) {
    final VirtualFile baseDir = DataAccessors.PROJECT_BASE_DIR.from(dataContext);
    if (baseDir == null) {
      return null;
    }
    return VfsUtil.virtualToIoFile(baseDir).getPath();
  }
}