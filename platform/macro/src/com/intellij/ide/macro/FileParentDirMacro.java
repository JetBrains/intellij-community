// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileParentDirMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "FileParentDir";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.parent.directory");
  }

  @Override
  public String expand(@NotNull DataContext dataContext, String @NotNull ... args) throws ExecutionCancelledException {
    if(args.length == 0 || StringUtil.isEmpty(args[0])) {
      return expand(dataContext);
    }
    String param = args[0];
    VirtualFile vFile = getVirtualDirOrParent(dataContext);
    while (vFile != null && !param.equalsIgnoreCase(vFile.getName())) {
      vFile = vFile.getParent();
    }
    return parentPath(vFile);
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    VirtualFile vFile = getVirtualDirOrParent(dataContext);
    return parentPath(vFile);
  }

  private static String parentPath(@Nullable VirtualFile vFile) {
    if(vFile != null) {
      vFile = vFile.getParent();
    }
    return vFile != null ? getPath(vFile) : null;
  }
}
