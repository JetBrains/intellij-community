package com.intellij.util.ui;


import com.intellij.openapi.fileTypes.FileTypeManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class FileLabel extends JLabel {
  protected File myFile;
  private boolean myShowIcon = true;
  private static final int PREFFERED_TEXT_LENGTH = 50;
  private FilePathSplittingPolicy myPolicy = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR;

  public FileLabel() {
  }

  public void setShowIcon(boolean showIcon) {
    myShowIcon = showIcon;
  }

  public FileLabel(File file) {
    myFile = file;
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
    int packedWidth = getIconWidth() + getPrefferregWidth();
    setPreferredSize(new Dimension(packedWidth, getPreferredSize().height));
  }

  private int getPrefferregWidth() {
    return getFontMetrics(getFont()).stringWidth(myPolicy.getPresentableName(myFile, PREFFERED_TEXT_LENGTH));
  }

  public int getIconWidth() {
    Icon icon = getIcon();
    if (icon == null) return 0;
    return icon.getIconWidth() + getIconTextGap();
  }
}
