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
import com.intellij.openapi.editor.event.*;
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

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;


/**
 * User: lia
 */


class myCaretAdapter extends CaretAdapter {
  @Override
  public void caretPositionChanged(CaretEvent e) {
    Project project = e.getEditor().getProject();
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      if (openedFile != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
        LogicalPosition currentPos = e.getNewPosition();
        if (selectedTaskFile != null) {
          Window selectedTaskWindow = selectedTaskFile.getSelectedWindow();
          if (selectedTaskWindow == null) {
            Window currentWindow = selectedTaskFile.getTaskWindow(selectedEditor, currentPos);
            if (currentWindow == null) {
              HintManager.getInstance().showInformationHint(selectedEditor, "You should select some task window");
            }
            else {
              return;
            }
          }
          else {
            RangeHighlighter selectedRangeHighlighter = selectedTaskWindow.getRangeHighlighter();
            if (selectedRangeHighlighter != null) {
              int startOffset = selectedRangeHighlighter.getStartOffset();
              int endOffset = selectedRangeHighlighter.getEndOffset();
              int offset = selectedEditor.getDocument().getLineStartOffset(currentPos.line) + currentPos.column;
              if (startOffset <= offset && offset <= endOffset) {
                return;
                //selectedTaskFile.updateOffsets(selectedEditor);
              }
              else {
                DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

                AnAction action = ActionManager.getInstance().getAction("CheckAction");
                AnAction resolveAction = ActionManager.getInstance().getAction("ResolveAction");
                AnAction nextAction = ActionManager.getInstance().getAction("NextWindow");
                AnAction prevAction = ActionManager.getInstance().getAction("PrevWindowAction");
                defaultActionGroup.add(action);
                defaultActionGroup.add(resolveAction);
                defaultActionGroup.add(nextAction);
                defaultActionGroup.add(prevAction);
                ListPopup popUp =
                  JBPopupFactory.getInstance().createActionGroupPopup("What should we do with selected task window?", defaultActionGroup,
                                                                      DataManager.getInstance()
                                                                        .getDataContext(e.getEditor().getComponent()),
                                                                      JBPopupFactory.ActionSelectionAid.MNEMONICS, true);

                popUp.showInBestPositionFor(DataManager.getInstance().getDataContext(e.getEditor().getComponent()));
              }
            }
          }
        }
      }
    }
  }
}

class StudyEditorFactoryListener implements EditorFactoryListener {
  private static final Logger LOG = Logger.getInstance(StudyEditorFactoryListener.class.getName());

  class MyMouseListener extends EditorMouseAdapter {
    private final TaskFile myTaskFile;

    MyMouseListener(TaskFile taskFile) {
      myTaskFile = taskFile;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(e.getEditor().getDocument());
      StudyTaskManager taskManager = StudyTaskManager.getInstance(e.getEditor().getProject());
      TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
      Window selectedWindow = selectedTaskFile.getSelectedWindow();
      Editor editor = e.getEditor();
      LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
      Window window = myTaskFile.getTaskWindow(editor, pos);


      if (selectedWindow != null && selectedWindow != window) {
        DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

        AnAction action = ActionManager.getInstance().getAction("CheckAction");
        AnAction resolveAction = ActionManager.getInstance().getAction("ResolveAction");
        AnAction nextAction = ActionManager.getInstance().getAction("NextWindow");
        AnAction prevAction = ActionManager.getInstance().getAction("PrevWindowAction");
        defaultActionGroup.add(action);
        defaultActionGroup.add(resolveAction);
        defaultActionGroup.add(nextAction);
        defaultActionGroup.add(prevAction);
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
      if (selectedWindow == null) {
        editor.getMarkupModel().removeAllHighlighters();

        selectedTaskFile.setSelectedWindow(window);
        if (window.isResolveStatus()) {
          window.draw(editor, false, true);
        }
        else {
          window.draw(editor, true, true);
        }
      }
      else {
        if (!(selectedWindow == window)) {
          DefaultActionGroup defaultActionGroup = new DefaultActionGroup();

          AnAction action = ActionManager.getInstance().getAction("CheckAction");
          AnAction resolveAction = ActionManager.getInstance().getAction("ResolveAction");
          AnAction nextAction = ActionManager.getInstance().getAction("NextWindow");
          defaultActionGroup.add(action);
          defaultActionGroup.add(resolveAction);
          defaultActionGroup.add(nextAction);
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
                  //editor.getCaretModel().addCaretListener(new myCaretAdapter());
                  editor.getMarkupModel().removeAllHighlighters();
                  taskFile.drawAllWindows(editor);
                }
              }
              catch (Exception e) {
                e.printStackTrace();
                LOG.error(e.getMessage());
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
    event.getEditor().getMarkupModel().removeAllHighlighters();
    event.getEditor().getSelectionModel().removeSelection();
  }
}
