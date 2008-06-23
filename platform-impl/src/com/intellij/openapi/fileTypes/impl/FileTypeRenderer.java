package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class FileTypeRenderer extends DefaultListCellRenderer {
  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);

  private final FileTypeListProvider myFileTypeListProvider;

  public FileTypeRenderer(final FileTypeListProvider fileTypeListProvider) {
    myFileTypeListProvider = fileTypeListProvider;
  }

  public FileTypeRenderer() {
    this(new DefaultFileTypeListProvider());
  }

  public static interface FileTypeListProvider {
    Iterable<FileType> getCurrentFileTypeList();
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    FileType type = (FileType)value;
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    final Icon icon = type.getIcon();
    if (icon != null) {
      layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
    }

    setIcon(layeredIcon);

    if (isDuplicated(type.getDescription())) {
      setText(type.getDescription() + " (" + type.getName() + ")");

    }
    else {
      setText(type.getDescription());
    }
    return this;
  }

  private boolean isDuplicated(final String description) {
    boolean found = false;

    for (FileType type : myFileTypeListProvider.getCurrentFileTypeList()) {
      if (description.equals(type.getDescription())) {
        if (!found) {
          found = true;
        }
        else {
          return true;
        }
      }
    }
    return false;
  }

  public Dimension getPreferredSize() {
    return new Dimension(0, 20);
  }

  private static class DefaultFileTypeListProvider implements FileTypeListProvider {
    private final java.util.List<FileType> myFileTypes;

    public DefaultFileTypeListProvider() {
      myFileTypes = Arrays.asList(FileTypeManager.getInstance().getRegisteredFileTypes());
    }

    public Iterable<FileType> getCurrentFileTypeList() {
      return myFileTypes;
    }
  }
}
