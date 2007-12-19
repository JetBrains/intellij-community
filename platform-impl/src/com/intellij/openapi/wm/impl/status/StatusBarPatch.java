package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.editor.Editor;

import javax.swing.*;

/**
 * @author cdr
 */
public interface StatusBarPatch {

  JComponent getComponent();
  //returns updated tooltip/text
  
  String updateStatusBar(Editor selected, final JComponent componentSelected);
  void clear();
}
