package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;

public class HttpUrlCellAppearance extends ValidFileCellAppearance {
  public HttpUrlCellAppearance(VirtualFile file) {
    super(file);
  }

  protected Icon getIcon() {
    return Icons.WEB_ICON;
  }
}
