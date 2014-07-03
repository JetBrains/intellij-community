package ru.compscicenter.edide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.*;

/**
 * author: liana
 * data: 6/27/14.
 * move caret to next task window
 */
public class NextWindowAction extends AnAction {

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
          selectedTaskFile.updateOffsets(selectedEditor);
          Window selectedWindow = selectedTaskFile.getSelectedWindow();
          boolean ifDraw = false;
          for (Window window : selectedTaskFile.getWindows()) {
            if (ifDraw) {
              selectedEditor.getMarkupModel().removeAllHighlighters();
              selectedTaskFile.setSelectedWindow(window);
              window.draw(selectedEditor, !window.isResolveStatus(), true);
              return;
            }
            if (window == selectedWindow) {
              ifDraw = true;
            }
          }
          //TODO:propose user to do next action
        }
      }
    }
  }
}
