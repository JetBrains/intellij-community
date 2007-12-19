package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;

import javax.swing.*;

public abstract class FileChooserAction extends AnAction {
  protected FileChooserAction() {
  }

  protected FileChooserAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  final public void actionPerformed(AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    actionPerformed(tree, e);
  }

  final public void update(AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    if (tree != null) {
      e.getPresentation().setEnabled(true);
      update(tree, e);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected abstract void update(FileSystemTree fileChooser, AnActionEvent e);

  protected abstract void actionPerformed(FileSystemTree fileChooser, AnActionEvent e);
}