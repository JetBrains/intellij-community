/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class FileDirPathFromParentMacro extends Macro implements MacroWithParams{

  private static final String PATH_DELIMITER = "/";
  private String myCachedDirUrl = "";
  @NotNull
  @Override
  public String getName() {
    return "FileDirPathFromParent";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeCoreBundle.message("macro.file.directory.from.parent");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    return null;
  }

  @Override
  public void cachePreview(@NotNull DataContext dataContext) {
    super.cachePreview(dataContext);
    myCachedDirUrl = ObjectUtils.doIfNotNull(getVirtualDirOrParent(dataContext), VirtualFile::getUrl);
  }

  @Override
  public String expand(@NotNull DataContext dataContext, String @NotNull ... args) throws ExecutionCancelledException {
    if(args.length == 0) {
      return super.expand(dataContext, args);
    }
    VirtualFile dir = getVirtualDirOrParent(dataContext);
    if (dir == null && myCachedDirUrl != null) {
      dir = VirtualFileManager.getInstance().findFileByUrl(myCachedDirUrl);
    }
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

  @NotNull
  private static String surroundWithSlashes(@NotNull String path) {
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
