
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseAllEditorsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, new Runnable(){
        public void run() {
          final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
          if (window != null){
            final VirtualFile[] files = window.getFiles();
            for (int i = 0; i < files.length; i++) {
              VirtualFile file = files[i];
              window.closeFile(file);
            }
            return;
          }
          FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
          VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
          VirtualFile[] openFiles = fileEditorManager.getSiblings(selectedFile);
          for (int i = 0; i < openFiles.length; i++) {
            fileEditorManager.closeFile(openFiles[i]);
          }
        }
      }, IdeBundle.message("command.close.all.editors"), null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final EditorWindow editorWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (editorWindow != null && editorWindow.inSplitter()) {
      presentation.setText(IdeBundle.message("action.close.all.editors.in.tab.group"));
    }
    else {
      presentation.setText(IdeBundle.message("action.close.all.editors"));
    }
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
  }
}
