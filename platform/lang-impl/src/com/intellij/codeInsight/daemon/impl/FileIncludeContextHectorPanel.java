// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class FileIncludeContextHectorPanel extends HectorComponentPanel {
  private ComboboxWithBrowseButton myContextFile;
  private JPanel myPanel;
  private final PsiFile myFile;
  private final FileIncludeManager myIncludeManager;

  public FileIncludeContextHectorPanel(PsiFile file, FileIncludeManager includeManager) {
    myFile = file;
    myIncludeManager = includeManager;

    myPanel.setBackground(UIUtil.getToolTipActionBackground());
    myContextFile.setBackground(UIUtil.getToolTipActionBackground());
    reset();
  }


  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
    JComboBox comboBox = myContextFile.getComboBox();

    comboBox.setRenderer(new MyListCellRenderer(comboBox));
    VirtualFile[] includingFiles = myIncludeManager.getIncludingFiles(myFile.getVirtualFile(), false);
    comboBox.setModel(new DefaultComboBoxModel(includingFiles));
    myContextFile.setTextFieldPreferredWidth(30);
  }

  private final class MyListCellRenderer extends DefaultListCellRenderer {
    private final JComboBox myComboBox;
    private int myMaxWidth;

    MyListCellRenderer(JComboBox comboBox) {
      myComboBox = comboBox;
      myMaxWidth = comboBox.getPreferredSize().width;
    }

    @Override
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {

      Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      String path = getPath(value);
      if (path != null) {
        int max = index == -1 ? myComboBox.getWidth() - myContextFile.getButton().getWidth() : myComboBox.getWidth() * 3;
        path = trimPath(path, myComboBox, "/", max);
        setText(path);
      }
      return rendererComponent;
    }

    private @Nullable @NlsSafe String getPath(Object value) {
      VirtualFile file = (VirtualFile)value;
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myFile.getProject()).getFileIndex();
      if (file != null) {
        VirtualFile root = fileIndex.getSourceRootForFile(file);
        if (root == null) {
          root = fileIndex.getContentRootForFile(file);
        }
        if (root != null) {
          return VfsUtilCore.getRelativePath(file, root, '/');
        }
      }
      return null;
    }

    private @NlsSafe String trimPath(String path, Component component, String separator, int length) {

      FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
      int maxWidth = fontMetrics.stringWidth(path);
      if (maxWidth <= length) {
        myMaxWidth = Math.max(maxWidth, myMaxWidth);
        return path;
      }
      StringBuilder result = new StringBuilder(path);
      if (path.startsWith(separator)) {
        result.delete(0, 1);
      }
      String[] strings = result.toString().split(separator);
      result.replace(0, strings[0].length(), "...");
      for (int i = 1; i < strings.length; i++) {
        String clipped = result.toString();
        int width = fontMetrics.stringWidth(clipped);
        if (width <= length) {
          myMaxWidth = Math.max(width, myMaxWidth);
          return clipped;
        }
        result.delete(4, 5 + strings[i].length());
      }
      return result.toString();
    }

  }

}
