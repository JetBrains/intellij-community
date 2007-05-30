package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

public class SelectionHistoryDialog extends FileHistoryDialog {
  private int myFrom;
  private int myTo;

  public SelectionHistoryDialog(IdeaGateway gw, VirtualFile f, int from, int to) {
    super(gw, f, false);
    myFrom = from;
    myTo = to;
    init();
  }
}
