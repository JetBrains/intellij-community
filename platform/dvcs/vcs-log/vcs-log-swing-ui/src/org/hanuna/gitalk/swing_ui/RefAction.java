package org.hanuna.gitalk.swing_ui;


import com.intellij.vcs.log.Ref;

public interface RefAction {
  void perform(Ref ref);
}
