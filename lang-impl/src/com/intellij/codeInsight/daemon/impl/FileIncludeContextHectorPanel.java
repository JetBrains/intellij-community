package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIncludeManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author mike
 */
public class FileIncludeContextHectorPanel extends HectorComponentPanel {
  private ComboboxWithBrowseButton myContextFile;
  private JPanel myPanel;
  private final PsiFile myFile;
  private final PsiIncludeManager myIncludeManager;

  public FileIncludeContextHectorPanel(final PsiFile file, final PsiIncludeManager includeManager) {
    myFile = file;
    myIncludeManager = includeManager;

    reset();
  }


  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
    final JComboBox comboBox = myContextFile.getComboBox();

    comboBox.setRenderer(new MyListCellRenderer(comboBox));
    final PsiFile[] includingFiles = myIncludeManager.getIncludingFiles(myFile);
    comboBox.setModel(new DefaultComboBoxModel(includingFiles));
    myContextFile.setTextFieldPreferredWidth(30);
  }

  public void disposeUIResources() { }

  private class MyListCellRenderer extends DefaultListCellRenderer {
    private final JComboBox myComboBox;
    private int myMaxWidth;

    public MyListCellRenderer(final JComboBox comboBox) {
      myComboBox = comboBox;
      myMaxWidth = comboBox.getPreferredSize().width;
    }

    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {

      final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      String path = getPath(value);
      if (path != null) {
        final int max = index == -1 ? myComboBox.getWidth() - myContextFile.getButton().getWidth() : myComboBox.getWidth() * 3;
        path = trimPath(path, myComboBox, "/", max);
        setText(path);
      }
      return rendererComponent;
    }

    @Nullable
    protected String getPath(final Object value) {
      final PsiFile psiFile = (PsiFile)value;
      //String path = WebUtil.getWebUtil().getWebPath(psiFile);
      String path = null;
      if (path == null) {
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
        VirtualFile file = psiFile.getVirtualFile();
        final PsiFile originalFile = psiFile.getOriginalFile();
        if (file == null && originalFile != null) {
          file = originalFile.getVirtualFile();
        }
        if (file != null) {
          VirtualFile root = fileIndex.getSourceRootForFile(file);
          if (root == null) {
            root = fileIndex.getContentRootForFile(file);
          }
          if (root != null) {
            path = VfsUtil.getRelativePath(file, root, '/');
          }
        }
      }
      return path;
    }

    private String trimPath(String path, Component component, String separator, int length) {

      final FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
      final int maxWidth = fontMetrics.stringWidth(path);
      if (maxWidth <= length) {
        myMaxWidth = Math.max(maxWidth, myMaxWidth);
        return path;
      }
      final StringBuilder result = new StringBuilder(path);
      if (path.startsWith(separator)) {
        result.delete(0, 1);
      }
      final String[] strings = result.toString().split(separator);
      result.replace(0, strings[0].length(), "...");
      for (int i = 1; i < strings.length; i++) {
        final String clipped = result.toString();
        final int width = fontMetrics.stringWidth(clipped);
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
