package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Refreshes the VCS Log, completely, or partly.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogRefresher {

  Topic<VcsLogRefresher> TOPIC = Topic.create(VcsLogRefresher.class.getName(), VcsLogRefresher.class);

  /**
   * Makes the log perform complete refresh for all roots.
   * It retrieves the data from the VCS and rebuilds the whole log.
   */
  void refreshCompletely();

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  void refresh(@NotNull VirtualFile root);

  /**
   * Makes the log refresh only the reference labels for the given root.
   */
  void refreshRefs(@NotNull VirtualFile root);

}
