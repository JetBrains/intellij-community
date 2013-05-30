package org.hanuna.gitalk.swing_ui;

import org.hanuna.gitalk.refs.Ref;

public interface RefAction {
  void perform(Ref ref);
}
