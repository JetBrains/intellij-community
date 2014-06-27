package ru.compscicenter.edide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

/**
 * author: liana
 * data: 6/18/14.
 * Action for marking task window as resolved
 */
class ResolveAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(StudyDirectoryProjectGenerator.class.getName());


  //TODO:I believe this code is improvable
  private int getLineNumByOffset(Editor editor, int offset) {
    int i = 0;
    int lineStartOffset = editor.getDocument().getLineStartOffset(i);
    while (i < editor.getDocument().getLineCount() && lineStartOffset < offset) {
      i++;
      if (i < editor.getDocument().getLineCount()) {
        lineStartOffset = editor.getDocument().getLineStartOffset(i);
      }
    }
    return i - 1;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Editor selectedEditor = null;
    Project project = e.getProject();

    //getting selected editor
    FileEditor fileEditor =
      FileEditorManagerImpl.getInstanceEx(project).getSplitters().getCurrentWindow().getSelectedEditor().getSelectedEditorWithProvider()
        .getFirst();
    if (fileEditor instanceof StudyEditor) {
      FileEditor defaultEditor = ((StudyEditor)fileEditor).getDefaultEditor();
      if (defaultEditor instanceof PsiAwareTextEditorImpl) {
        selectedEditor = ((PsiAwareTextEditorImpl)defaultEditor).getEditor();
      }
    }


    if (selectedEditor == null) {
      return;
    }
    VirtualFile openedFile = FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
    if (openedFile != null) {
      try {
        TaskFile selectedTaskFile = StudyTaskManager.getInstance(project).getTaskFile(openedFile);
        if (selectedTaskFile != null) {
          Window selectedTaskWindow = StudyTaskManager.getInstance(project).getSelectedWindow();
          if (selectedTaskWindow != null) {
            RangeHighlighter selectedRangeHighlighter = selectedTaskWindow.getRangeHighlighter();
            int lineChange = selectedEditor.getDocument().getLineCount() - selectedTaskFile.getLineNum();
            if (lineChange != 0) {
              int newStartLine = getLineNumByOffset(selectedEditor, selectedRangeHighlighter.getStartOffset());
              int newEndLine = getLineNumByOffset(selectedEditor, selectedRangeHighlighter.getEndOffset());
              selectedTaskFile.increment(newStartLine, lineChange);
              selectedTaskWindow.setLine(selectedTaskWindow.getLine() - lineChange);
              selectedTaskFile.incrementAfterOffset(newEndLine, 0, selectedRangeHighlighter.getEndOffset() -
                                                                   selectedEditor.getDocument().getLineStartOffset(newEndLine));
            }
            else {
              int oldEnd = selectedTaskWindow.getRealStartOffset(selectedEditor) + selectedTaskWindow.getOffsetInLine();
              int endChange = selectedRangeHighlighter.getEndOffset() - oldEnd;
              selectedTaskFile.incrementAfterOffset(selectedTaskWindow.getLine(), selectedTaskWindow.getStart(), endChange);
            }
            int newLength = selectedRangeHighlighter.getEndOffset() - selectedRangeHighlighter.getStartOffset();
            selectedTaskWindow.setOffsetInLine(newLength);
            selectedTaskWindow.setResolveStatus(true);
            selectedEditor.getMarkupModel().removeAllHighlighters();
            selectedTaskWindow.draw(selectedEditor, false);
            FileDocumentManager.getInstance().saveAllDocuments();
            FileDocumentManager.getInstance().reloadFiles(openedFile);
          }
        }
      }
      catch (NullPointerException exp) {
        LOG.error(exp.getStackTrace());
      }
    }
  }
}
