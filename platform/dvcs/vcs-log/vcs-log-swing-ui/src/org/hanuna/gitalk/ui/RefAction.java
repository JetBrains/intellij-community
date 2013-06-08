package org.hanuna.gitalk.ui;


import com.intellij.vcs.log.Ref;

public interface RefAction {
  void perform(Ref ref);
}
