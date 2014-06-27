package ru.compscicenter.edide;


import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

import java.util.Arrays;


/**
 * User: lia
 */


class StudyEditorFactoryListener implements EditorFactoryListener {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());
  class MyMouseListener extends EditorMouseAdapter {
    private final TaskFile myTaskFile;

    MyMouseListener(TaskFile taskFile) {
      myTaskFile = taskFile;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      Window selectedWindow = StudyTaskManager.getInstance(e.getEditor().getProject()).getSelectedWindow();
      Editor editor = e.getEditor();
      LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
      Window window = myTaskFile.getTaskWindow(editor, pos);


      if (selectedWindow!= null && selectedWindow != window) {
        DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

        AnAction action = ActionManager.getInstance().getAction("ru.compscicenter.edide.CheckAction");
        AnAction resolveAction = ActionManager.getInstance().getAction("ru.compscicenter.edide.ResolveAction");
        defaultActionGroup.add(action);
        defaultActionGroup.add(resolveAction);
        ListPopup popUp =
          JBPopupFactory.getInstance().createActionGroupPopup("What should we do with selected task window?", defaultActionGroup,
                                                              DataManager.getInstance().getDataContext(e.getEditor().getComponent()),
                                                              JBPopupFactory.ActionSelectionAid.MNEMONICS, true);

        popUp.showInBestPositionFor(DataManager.getInstance().getDataContext(e.getEditor().getComponent()));
        return;
      }

      if (window == null) {
        myTaskFile.drawAllWindows(editor);
        return;
      }
      StudyTaskManager taskManager = StudyTaskManager.getInstance(e.getEditor().getProject());
      if (taskManager.getSelectedWindow() == null) {
        editor.getMarkupModel().removeAllHighlighters();

        taskManager.setSelectedWindow(window);
        if (window.isResolveStatus()) {
          window.draw(editor, false);
        }
        else {
          window.draw(editor, true);
        }
      }
      else {
        if (!(taskManager.getSelectedWindow() == window)) {
          DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

          AnAction action = ActionManager.getInstance().getAction("ru.compscicenter.edide.CheckAction");
          AnAction resolveAction = ActionManager.getInstance().getAction("ru.compscicenter.edide.ResolveAction");
          defaultActionGroup.add(action);
          defaultActionGroup.add(resolveAction);
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
                  ru.compscicenter.edide.course.TaskFile taskFile = taskManager.getTaskFile(openedFile);
                  if (taskFile == null) {
                    return;
                  }
                  taskFile.setLineNum(editor.getDocument().getLineCount());
                  editor.addEditorMouseListener(new MyMouseListener(taskFile));
                  editor.getMarkupModel().removeAllHighlighters();
                  taskFile.drawAllWindows(editor);
                }
              }
              catch (Exception e) {
                LOG.error(e.getStackTrace());
              }
            }
          });
        }
      }
    );
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    LOG.info("Editor released\n");
  }
}
