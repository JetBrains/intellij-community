/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;


import com.intellij.openapi.fileTypes.FileTypeManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class FileLabel extends JLabel {
  protected File myFile;
  private boolean myShowIcon = true;
  private static final int PREFERRED_TEXT_LENGTH = 50;
  private final FilePathSplittingPolicy myPolicy = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR;

  public FileLabel() {}

  public FileLabel(File file) {
    myFile = file;
  }

  public void setShowIcon(boolean showIcon) {
    myShowIcon = showIcon;
  }

  public static String getFilePath(File file) {
    return file.getPath();
  }

  public void setFile(File ioFile) {
    myFile = ioFile;
    if (myShowIcon) {
      setIcon(FileTypeManager.getInstance().getFileTypeByFileName(myFile.getName()).getIcon());
    }
    else {
      setIcon(null);
    }
  }

  public String getText() {
    if (myFile == null) return "";
    int width = getWidth();
    if (getIcon() != null) width -= getIconWidth();
    return myPolicy.getOptimalTextForComponent(myFile, this, width);
  }

  public void pack() {
    setPreferredSize(new Dimension(getPreferredWidth(), getPreferredSize().height));
  }

  protected int getPreferredWidth() {
    return getIconWidth() + getFontMetrics(getFont()).stringWidth(myPolicy.getPresentableName(myFile, PREFERRED_TEXT_LENGTH));
  }

  public int getIconWidth() {
    Icon icon = getIcon();
    if (icon == null) return 0;
    return icon.getIconWidth() + getIconTextGap();
  }
}
