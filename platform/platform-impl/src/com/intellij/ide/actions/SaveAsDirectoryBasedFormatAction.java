package com.intellij.ide.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.Collection;

/**
 * @author spleaner
 */
public class SaveAsDirectoryBasedFormatAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project instanceof ProjectEx) {
      final IProjectStore projectStore = ((ProjectEx)project).getStateStore();
      if (StorageScheme.DIRECTORY_BASED != projectStore.getStorageScheme()) {
        final int result = Messages.showOkCancelDialog(project,
                                                       "Project will be saved and reopened in new Directory-Based format.\nAre you sure you want to continue?",
                                                       "Save project to Directory-Based format", Messages.getWarningIcon());
        if (result == 0) {
          final VirtualFile baseDir = project.getBaseDir();
          assert baseDir != null;

          File ideaDir = new File(baseDir.getPath(), ProjectEx.DIRECTORY_STORE_FOLDER + File.separatorChar);
          final boolean ok = (ideaDir.exists() && ideaDir.isDirectory()) || ideaDir.mkdirs();
          if (ok) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ideaDir);


            final StateStorageManager storageManager = projectStore.getStateStorageManager();
            for (String file : storageManager.getStorageFileNames()) {
              storageManager.clearStateStorage(file);
            }

            projectStore.setProjectFilePath(baseDir.getPath());
            project.save();
            ProjectUtil.closeProject(project);
            ProjectUtil.openProject(baseDir.getPath(), null, false);
          }
          else {
            Messages.showErrorDialog(project, String.format("Unable to create '.idea' directory (%s)", ideaDir), "Error saving project!");
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    boolean visible = project != null;

    if (project instanceof ProjectEx) {
      visible = ((ProjectEx)project).getStateStore().getStorageScheme() != StorageScheme.DIRECTORY_BASED;
    }

    presentation.setVisible(visible);
  }
}
