// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileDirPathFromParentMacro extends Macro implements MacroWithParams{

  private static final String PATH_DELIMITER = "/";
  @Override
  public @NotNull String getName() {
    return "FileDirPathFromParent";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.directory.from.parent");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    return null;
  }

  @Override
  public String expand(@NotNull DataContext dataContext, String @NotNull ... args) throws ExecutionCancelledException {
    if(args.length == 0) {
      return super.expand(dataContext, args);
    }
    VirtualFile dir = getVirtualDirOrParent(dataContext);
    if (dir == null) {
      return "";
    }
    String dirPath = dir.getPath();
    String surroundedSubDir = surroundWithSlashes(FileUtil.toSystemIndependentName(args[0]));
    String surroundedDirPath = surroundWithSlashes(dirPath);
    if (surroundedSubDir.length() == 1) {
      return FileUtil.toSystemDependentName(surroundedDirPath.substring(1));
    }
    int ind = lastIndexOf(surroundedDirPath,
                          surroundedSubDir,
                          surroundedDirPath.length(),
                          !dir.isCaseSensitive());
    if (ind >= 0) {
      return FileUtil.toSystemDependentName(surroundedDirPath.substring(ind + surroundedSubDir.length()));
    }
    return FileUtil.toSystemDependentName(dirPath.endsWith(PATH_DELIMITER) ? dirPath : dirPath + PATH_DELIMITER);
  }

  private static int lastIndexOf(@NotNull CharSequence buffer, @NotNull String pattern, int maxIndex, boolean ignoreCase) {
    int patternLength = pattern.length();
    int end = buffer.length() - patternLength;
    if (maxIndex > end) {
      maxIndex = end;
    }
    for (int i = maxIndex; i >= 0; i--) {
      boolean found = true;
      for (int j = 0; j < patternLength; j++) {
        if (ignoreCase) {
          if (!StringUtil.charsEqualIgnoreCase(pattern.charAt(j), buffer.charAt(i + j))) {
            found = false;
            break;
          }
        }
        else {
          if (pattern.charAt(j) != buffer.charAt(i + j)) {
            found = false;
            break;
          }
        }
      }
      if (found) {
        return i;
      }
    }
    return -1;
  }

  private static @NotNull String surroundWithSlashes(@NotNull String path) {
    if (path.isEmpty()) {
      return PATH_DELIMITER;
    }
    boolean prepend = !path.startsWith(PATH_DELIMITER);
    boolean append = !path.endsWith(PATH_DELIMITER);
    if (prepend && append) {
      return PATH_DELIMITER + path + PATH_DELIMITER;
    }
    if (prepend) {
      path = PATH_DELIMITER + path;
    }
    if (append) {
      path += PATH_DELIMITER;
    }
    return path;
  }
}
