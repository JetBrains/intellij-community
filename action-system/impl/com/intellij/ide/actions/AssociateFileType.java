package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class AssociateFileType extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    FileTypeChooser.associateFileType(file.getName());
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    boolean haveSmthToDo;
    if (project == null || file == null || file.isDirectory()) {
      haveSmthToDo = false;
    }
    else {
      haveSmthToDo = FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.UNKNOWN;
    }
    presentation.setVisible(haveSmthToDo || ActionPlaces.MAIN_MENU.equals(e.getPlace()));
    presentation.setEnabled(haveSmthToDo);
  }

}
