package ru.compscicenter.edide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.course.*;

/**
 * author: liana
 * data: 6/27/14.
 */
public class NextWindowAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    Window selectedTaskWindow = taskManager.getSelectedWindow();
    Editor selectedEditor = null;
    if (selectedTaskWindow != null) {
      //getting selected editor
      FileEditor fileEditor =
        FileEditorManagerImpl.getInstanceEx(project).getSplitters().getCurrentWindow().getSelectedEditor().getSelectedEditorWithProvider()
          .getFirst();
      if (fileEditor instanceof StudyEditor) {
        FileEditor defaultEditor = ((StudyEditor)fileEditor).getDefaultEditor();
        if (defaultEditor instanceof PsiAwareTextEditorImpl) {
          selectedEditor = ((PsiAwareTextEditorImpl)defaultEditor).getEditor();
        }
        if (selectedEditor != null) {
          VirtualFile openedFile = FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
          ru.compscicenter.edide.course.TaskFile selectedTaskFile = StudyTaskManager.getInstance(project).getTaskFile(openedFile);
          boolean toDraw = false;
          for (Window taskWindow:selectedTaskFile.getWindows()) {
            if (toDraw) {
              selectedEditor.getMarkupModel().removeAllHighlighters();
              taskManager.setSelectedWindow(taskWindow);
              taskWindow.draw(selectedEditor, !taskWindow.isResolveStatus());
              return;
            }
            if (taskWindow == selectedTaskWindow) {
              toDraw = true;
            }
          }

        }
      }
    }
  }
}
