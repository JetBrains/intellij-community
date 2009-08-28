package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

public final class GotoModuleDirectory extends FileChooserAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.GotoModuleDirectory");

  protected void actionPerformed(final FileSystemTree fileSystemTree, AnActionEvent e) {
    final VirtualFile path = getModulePath(e);
    LOG.assertTrue(path != null);
    fileSystemTree.select(path, new Runnable() {
      public void run() {
        fileSystemTree.expand(path, null);
      }
    });
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final VirtualFile path = getModulePath(e);
    presentation.setEnabled(path != null && fileSystemTree.isUnderRoots(path));
  }

  private static VirtualFile getModulePath(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) {
      module = e.getData(LangDataKeys.MODULE);
    }
    if (module == null) {
      return null;
    }
    final VirtualFile moduleFile = validated(module.getModuleFile());
    return (moduleFile != null)? validated(moduleFile.getParent()) : null;
  }

  private static VirtualFile validated(final VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    return file;
  }

}
