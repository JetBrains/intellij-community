package ru.compscicenter.edide;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.TaskWindow;

/**
 * author: liana
 * data: 7/16/14.
 * Listens changes in study files and updates
 * coordinates of all the windows in current task file
 */
public class StudyDocumentListener extends DocumentAdapter {
  private final TaskFile myTaskFile;
  private int oldLine;
  private int oldLineStartOffset;

  public StudyDocumentListener(TaskFile taskFile) {
    myTaskFile = taskFile;
  }


  //remembering old end before document change because of problems
  // with fragments containing "\n"
  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    int oldEnd = e.getOffset() + e.getOldLength();
    Document document = e.getDocument();
    oldLine = document.getLineNumber(oldEnd);
    oldLineStartOffset = document.getLineStartOffset(oldLine);
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (e instanceof DocumentEventImpl) {
      DocumentEventImpl event = (DocumentEventImpl)e;
      Document document = e.getDocument();
      int offset = e.getOffset();
      int change = event.getNewLength() - event.getOldLength();
      int line = document.getLineNumber(offset);
      int offsetInLine = offset - document.getLineStartOffset(line);
      LogicalPosition pos = new LogicalPosition(line, offsetInLine);
      TaskWindow taskWindow = myTaskFile.getTaskWindow(document, pos);
      if (taskWindow != null) {
        int newLength = taskWindow.getLength() + change;
        taskWindow.setLength(newLength);
      }
      int newEnd = offset + event.getNewLength();
      int newLine = document.getLineNumber(newEnd);
      int lineChange = newLine - oldLine;
      myTaskFile.incrementLines(oldLine + 1, lineChange);
      int newEndOffsetInLine = offset + e.getNewLength() - document.getLineStartOffset(newLine);
      int oldEndOffsetInLine = offset + e.getOldLength() - oldLineStartOffset;
      myTaskFile.updateLine(lineChange, oldLine, newEndOffsetInLine, oldEndOffsetInLine);
    }
  }
}
