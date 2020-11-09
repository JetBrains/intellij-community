// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author gregsh
 */
public final class ScratchUtil {
  private ScratchUtil() {
  }

  /**
   * Returns true if a file or a directory is in one of scratch roots: scratch, console, etc.
   *
   * @see RootType
   * @see ScratchFileService
   */
  public static boolean isScratch(@Nullable VirtualFile file) {
    RootType rootType = RootType.forFile(file);
    return rootType != null && !rootType.isHidden();
  }

  @NotNull
  public static String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
    RootType rootType = Objects.requireNonNull(RootType.forFile(file));
    String rootPath = ScratchFileService.getInstance().getRootPath(rootType);
    VirtualFile rootFile = LocalFileSystem.getInstance().findFileByPath(rootPath);
    if (rootFile == null || !VfsUtilCore.isAncestor(rootFile, file, false)) {
      throw new AssertionError(file.getPath());
    }
    StringBuilder sb = new StringBuilder();
    for (VirtualFile o = file; !rootFile.equals(o); o = o.getParent()) {
      String part = StringUtil.notNullize(rootType.substituteName(project, o), o.getName());
      if (sb.length() == 0 && part.indexOf('/') > -1) {
        // db console root type adds folder here, trim it
        part = part.substring(part.lastIndexOf('/') + 1);
      }
      sb.insert(0, "/" + part);
    }
    sb.insert(0, rootType.getDisplayName());
    if (sb.charAt(sb.length() - 1) == ']') {
      // db console root type adds [data source name] here, trim it
      int idx = sb.lastIndexOf(" [");
      if (idx > 0 && sb.indexOf("/" + sb.substring(idx + 2, sb.length() - 1) + "/") < idx) {
        sb.setLength(idx);
      }
    }
    return sb.toString();
  }
}
