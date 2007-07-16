package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public boolean isFileSelectable(final VirtualFile file) {
        return isProjectDirectory(file) || isProjectFile(file);
      }

      public Icon getOpenIcon(final VirtualFile virtualFile) {
        if (isProjectDirectory(virtualFile)) return Icons.PROJECT_ICON;
        final Icon icon = getImporterIcon(virtualFile, true);
        if(icon!=null){
          return icon;
        }
        return super.getOpenIcon(virtualFile);
      }
      public Icon getClosedIcon(final VirtualFile virtualFile) {
        if (isProjectDirectory(virtualFile)) return Icons.PROJECT_ICON;
        final Icon icon = getImporterIcon(virtualFile, false);
        if(icon!=null){
          return icon;
        }
        return super.getClosedIcon(virtualFile);
      }

      @Nullable
      public Icon getImporterIcon(final VirtualFile virtualFile, final boolean open) {
        final ProjectOpenProcessor provider = ProjectUtil.getImportProvider(virtualFile);
        if(provider!=null) {
          return provider.getIcon();
        }
        return null;
      }

      public boolean isFileVisible(final VirtualFile file, final boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || isProjectFile(file));
      }
    };
    descriptor.setTitle(IdeBundle.message("title.open.project"));
    descriptor.setDescription(IdeBundle.message("filter.project.files"));
    final VirtualFile[] files = FileChooser.chooseFiles(project, descriptor);

    if (files.length == 0 || files[0] == null) return;

    ProjectUtil.openOrImport(files[0].getPath(), project, false);
  }

  private static boolean isProjectFile(final VirtualFile file) {
    return (!file.isDirectory() && file.getName().toLowerCase().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) ||
           (ProjectUtil.getImportProvider(file) != null);
  }

  private static boolean isProjectDirectory(final VirtualFile virtualFile) {
    if (virtualFile.isDirectory() && virtualFile.findChild(Project.DIRECTORY_STORE_FOLDER) != null) return true;
    return false;
  }
}