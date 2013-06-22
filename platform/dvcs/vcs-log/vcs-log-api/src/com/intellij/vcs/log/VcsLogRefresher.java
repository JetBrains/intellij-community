package com.intellij.vcs.log;

import com.intellij.util.messages.Topic;

/**
 * @author Kirill Likhodedov
 */
public interface VcsLogRefresher {

  Topic<VcsLogRefresher> TOPIC = Topic.create(VcsLogRefresher.class.getName(), VcsLogRefresher.class);

  /**
   * Call to make the log perform complete refresh.
   */
  void refreshAll();

  /**
   * Call to make the log refresh only reference labels.
   */
  void refreshRefs();

}
