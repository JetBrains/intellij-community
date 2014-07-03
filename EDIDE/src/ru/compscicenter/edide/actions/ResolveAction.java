package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

/**
 * author: liana
 * data: 6/18/14.
 * Action for marking task window as resolved
 */

class ResolveAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor == null) {
      return;
    }
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    if (openedFile != null) {
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
      if (selectedTaskFile != null) {
        selectedTaskFile.resolveSelectedTaskWindow(project, selectedEditor);
        Window selectedWindow = taskManager.getSelectedWindow();
        selectedWindow.setResolveStatus(true);
        selectedEditor.getMarkupModel().removeAllHighlighters();
        selectedWindow.draw(selectedEditor, false);
        fileDocumentManager.saveAllDocuments();
        fileDocumentManager.reloadFiles(openedFile);
      }
    }
  }
}
