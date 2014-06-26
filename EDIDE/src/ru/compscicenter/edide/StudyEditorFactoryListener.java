package ru.compscicenter.edide;


import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import ru.compscicenter.edide.course.StudyTaskWindow;
import ru.compscicenter.edide.course.TaskFile;

import java.util.Arrays;


/**
 * User: lia
 */


class StudyEditorFactoryListener implements EditorFactoryListener {

  class MyMouseListener extends EditorMouseAdapter {
    private final TaskFile myTaskFile;

    MyMouseListener(TaskFile taskFile) {
      myTaskFile = taskFile;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
      StudyTaskWindow taskWindow = myTaskFile.getTaskWindow(editor, pos);
      if (taskWindow == null) {
        myTaskFile.drawAllWindows(editor);
        return;
      }
      StudyTaskManager taskManager = StudyTaskManager.getInstance(e.getEditor().getProject());
      if (taskManager.getSelectedTaskWindow() == null) {
        editor.getMarkupModel().removeAllHighlighters();

        taskManager.setSelectedTaskWindow(taskWindow);
        if (taskWindow.getResolveStatus()) {
          taskWindow.draw(editor, false);
        }
        else {
          taskWindow.draw(editor, true);
        }
      }
      else {
        if (!(taskManager.getSelectedTaskWindow() == taskWindow)) {
          DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

          AnAction action = ActionManager.getInstance().getAction("ru.compscicenter.edide.CheckAction");
          defaultActionGroup.add(action);
          ListPopup popUp =
            JBPopupFactory.getInstance().createActionGroupPopup("What should we do with selected task window?", defaultActionGroup,
                                                                DataManager.getInstance().getDataContext(e.getEditor().getComponent()),
                                                                JBPopupFactory.ActionSelectionAid.MNEMONICS, true);

          popUp.showInBestPositionFor(DataManager.getInstance().getDataContext(e.getEditor().getComponent()));
        }
      }
    }
  }


  @Override
  public void editorCreated(@NotNull final EditorFactoryEvent event) {
    Project project = event.getEditor().getProject();
    if (project == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                final Editor editor = event.getEditor();

                VirtualFile openedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
                if (openedFile != null) {
                  HintManager.getInstance().showInformationHint(editor, "Select any window");
                  StudyTaskManager taskManager = StudyTaskManager.getInstance(editor.getProject());
                  TaskFile taskFile = taskManager.getTaskFile(openedFile);
                  if (taskFile == null) {
                    return;
                  }

                  editor.addEditorMouseListener(new MyMouseListener(taskFile));
                  editor.getMarkupModel().removeAllHighlighters();
                  taskFile.drawAllWindows(editor);
                  for (StudyTaskWindow window : taskFile.getWindows()) {
                    window.setResolveStatus(true);
                  }
                }
              }
              catch (Exception e) {
                Log.print("Something wrong with meta file:" + e.getCause());
                Log.print(Arrays.toString(e.getStackTrace()));
                Log.print(e.getMessage());
                Log.flush();
              }
            }
          });
        }
      }
    );
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Log.print("Editor released\n");
    Log.flush();
  }
}
