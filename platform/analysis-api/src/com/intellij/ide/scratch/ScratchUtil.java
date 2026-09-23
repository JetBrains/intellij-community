// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author gregsh
 */
public final class ScratchUtil {
  /** The id of the "Scratches" root type, which the scratch plugin registers. */
  public static final @NonNls String SCRATCHES_ROOT_TYPE_ID = "scratches";

  private ScratchUtil() {
  }

  /** @return the "Scratches" root type, or null when the scratch plugin is not loaded */
  public static @Nullable RootType findScratchesRootType() {
    for (RootType rootType : RootType.getAllRootTypes()) {
      if (SCRATCHES_ROOT_TYPE_ID.equals(rootType.getId())) return rootType;
    }
    return null;
  }

  /**
   * Returns true if a file is under the "Scratches" root, as opposed to a console or another root type.
   *
   * @see #isScratch(VirtualFile)
   */
  @Contract("null -> false")
  public static boolean isInScratchesRoot(@Nullable VirtualFile file) {
    RootType rootType = RootType.forFile(file);
    return rootType != null && SCRATCHES_ROOT_TYPE_ID.equals(rootType.getId());
  }

  /**
   * Returns true if a file or a directory is in one of scratch roots: scratch, console, etc.
   *
   * @see RootType
   * @see ScratchFileService
   */
  @Contract("null -> false")
  public static boolean isScratch(@Nullable VirtualFile file) {
    RootType rootType = RootType.forFile(file);
    return rootType != null && !rootType.isHidden();
  }

  public static @NotNull String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
    RootType rootType = Objects.requireNonNull(RootType.forFile(file));
    String rootPath = ScratchFileService.getInstance().getRootPath(rootType);
    VirtualFile rootFile = StandardFileSystems.local().findFileByPath(rootPath);
    if (rootFile == null || !VfsUtilCore.isAncestor(rootFile, file, false)) {
      throw new AssertionError(file.getPath());
    }
    StringBuilder sb = new StringBuilder();
    for (VirtualFile o = file; !rootFile.equals(o); o = o.getParent()) {
      String part = StringUtil.notNullize(rootType.substituteName(project, o), o.getName());
      if (sb.isEmpty() && part.indexOf('/') > -1) {
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
