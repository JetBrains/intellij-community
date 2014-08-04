package ru.compscicenter.edide.actions;

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
 * data: 6/30/14.
 */
public class PrevWindowAction extends DumbAwareAction {
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
          TaskWindow prev = null;
          for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
            if (taskWindow == selectedTaskWindow) {
              break;
            }
            prev = taskWindow;
          }

          if (prev != null) {
            selectedTaskFile.setSelectedTaskWindow(prev);
            prev.draw(selectedEditor, prev.getStatus() != StudyStatus.Solved, true);
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
