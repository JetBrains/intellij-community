// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The places of the moved items after the move. The analysis of a move computes the new paths on the original files: the edits run on
 * the copies before the files move. A path is a {@link VirtualFile#getPath()} of the file system of the moved items.
 */
public final class ModPlanMovePlaces {
  private final @NotNull Map<VirtualFile, String> myFuturePaths;

  /** @param futurePaths the path of each moved item after the move; a file inside a moved directory has no entry */
  ModPlanMovePlaces(@NotNull Map<VirtualFile, String> futurePaths) {
    myFuturePaths = Map.copyOf(futurePaths);
  }

  /**
   * @return the path of {@code file} after the move, if it is a moved item or is inside a moved directory; null if it does not move
   *
   * <pre>{@code
   * // move the directory /src/d into /src/e
   * futurePathOf(/src/d)            // "/src/e/d"
   * futurePathOf(/src/d/sub/a.txt)  // "/src/e/d/sub/a.txt"
   * futurePathOf(/src/f/b.txt)      // null
   * }</pre>
   */
  public @Nullable String futurePathOf(@NotNull VirtualFile file) {
    for (var ancestor = file; ancestor != null; ancestor = ancestor.getParent()) {
      var futurePath = myFuturePaths.get(ancestor);
      if (futurePath != null) {
        return ancestor.equals(file) ? futurePath : futurePath + "/" + VfsUtilCore.getRelativePath(file, ancestor, '/');
      }
    }
    return null;
  }
}