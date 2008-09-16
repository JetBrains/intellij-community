package com.intellij.platform;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class OpenDirectoryProjectAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(false);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    final VirtualFile[] files = dialog.choose(null, project);
    if (files.length > 0) {
      PlatformProjectOpenProcessor.getInstance().doOpenProject(files [0], null, false);
    }
  }
}
