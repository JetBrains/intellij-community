package org.hanuna.gitalk.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Managers colors used for the vcs log: references, roots, branches, etc.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogColorManager {

  /**
   * Returns the background color which should be used to paint a {@link VcsRef reference label}.
   */
  @NotNull
  Color getBackgroundColor(@NotNull VcsRef ref);

  /**
   * Returns the color assigned to the given repository root.
   */
  @NotNull
  Color getRootColor(@NotNull VirtualFile root);

  /**
   * Tells if there are several repositories currently shown in the log.
   */
  boolean isMultipleRoots();

}
