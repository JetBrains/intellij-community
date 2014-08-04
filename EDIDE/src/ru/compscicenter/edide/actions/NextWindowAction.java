package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.StudyUtils;
import ru.compscicenter.edide.course.StudyStatus;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.TaskWindow;
import ru.compscicenter.edide.editor.StudyEditor;

/**
 * author: liana
 * data: 6/27/14.
 * move caret to next task window
 */
public class NextWindowAction extends DumbAwareAction {

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      if (openedFile != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
        if (selectedTaskFile != null) {
          TaskWindow selectedTaskWindow = selectedTaskFile.getSelectedTaskWindow();
          boolean ifDraw = false;
          for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
            if (ifDraw) {
              selectedTaskFile.setSelectedTaskWindow(taskWindow);
              taskWindow.draw(selectedEditor, taskWindow.getStatus() != StudyStatus.Solved, true);
              return;
            }
            if (taskWindow == selectedTaskWindow) {
              ifDraw = true;
            }
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
