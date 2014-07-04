package ru.compscicenter.edide.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

/**
 * author: liana
 * data: 6/18/14.
 * mark selected task window as resolved
 * and update offsets in task file
 * or propose user to check task if no task windows are available
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
        selectedTaskFile.updateOffsets(selectedEditor);
        Window selectedWindow = selectedTaskFile.getSelectedWindow();
        selectedWindow.setResolveStatus(true);
        selectedEditor.getMarkupModel().removeAllHighlighters();
        selectedWindow.draw(selectedEditor, false, true);
        fileDocumentManager.saveAllDocuments();
        fileDocumentManager.reloadFiles(openedFile);
        Window nextWindow = selectedWindow.getNext();
        if (nextWindow == null) {
          if (selectedTaskFile.getTask().isResolved()) {
            DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

            AnAction checkAction = ActionManager.getInstance().getAction("ru.compscicenter.edide.actions.CheckAction");
            defaultActionGroup.add(checkAction);
            ListPopup popUp =
              JBPopupFactory.getInstance().createActionGroupPopup("What should we do with selected task window?", defaultActionGroup,
                                                                  DataManager.getInstance().getDataContext(selectedEditor.getComponent()),
                                                                  JBPopupFactory.ActionSelectionAid.MNEMONICS, true);

            popUp.showInBestPositionFor(DataManager.getInstance().getDataContext(selectedEditor.getComponent()));
          }
        }
      }
    }
  }
}
