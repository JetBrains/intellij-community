package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import ru.compscicenter.edide.StudyTaskManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:53
 */
public class TaskFile {
  private String name;
  private List<Window> windows;
  private int myLineNum = -1;
  private Task myTask;
  private Window mySelectedWindow = null;

  public Element saveState() {
    Element fileElement = new Element("file");
    fileElement.setAttribute("name", name);
    fileElement.setAttribute("myLineNum", Integer.toString(myLineNum));
    for (Window window : windows) {
      fileElement.addContent(window.saveState());
    }
    return fileElement;
  }

  public Task getTask() {
    return myTask;
  }

  public Window getSelectedWindow() {
    return mySelectedWindow;
  }

  public void setSelectedWindow(Window selectedWindow) {
    mySelectedWindow = selectedWindow;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Window> getWindows() {
    return windows;
  }

  public void setWindows(List<Window> windows) {
    this.windows = windows;
  }

  public int getLineNum() {
    return myLineNum;
  }

  public void setLineNum(int lineNum) {
    myLineNum = lineNum;
  }

  public void create(Project project, VirtualFile baseDir, File resourseRoot) throws IOException {
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    String systemIndependentResourceRootName = FileUtil.toSystemIndependentName(resourseRoot.getName());
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    FileUtil.copy(new File(resourseRoot, name), new File(baseDir.getPath(), systemIndependentName));
  }

  public void drawAllWindows(Editor editor) {
    for (Window window : windows) {
      window.draw(editor, false, false);
    }
  }

  public Window getTaskWindow(Editor editor, LogicalPosition pos) {
    int line = pos.line;
    if (line >= editor.getDocument().getLineCount()) {
      return null;
    }
    int column = pos.column;
    int realOffset = editor.getDocument().getLineStartOffset(line) + column;
    for (Window tw : windows) {
      if (line == tw.getLine()) {
        int twStartOffset = tw.getRealStartOffset(editor);
        int twEndOffset = twStartOffset + tw.getText().length();
        if (twStartOffset < realOffset && realOffset < twEndOffset) {
          return tw;
        }
      }
    }
    return null;
  }


  public void incrementAfterOffset(int line, int afterOffset, int change) {
    for (Window taskWindow : windows) {
      if (taskWindow.getLine() == line && taskWindow.getStart() > afterOffset) {
        taskWindow.setStart(taskWindow.getStart() + change);
      }
    }
  }

  public void increment(int startLine, int change) {
    for (Window taskWindow : windows) {
      if (taskWindow.getLine() >= startLine) {
        taskWindow.setLine(taskWindow.getLine() + change);
      }
    }
  }


  public void setParents(Task task) {
    myTask = task;
    for (Window window : windows) {
      window.setParent(this);
    }
    Collections.sort(windows);
  }

  public void setNewOffsetInLine(int startLine, int startOffset, int defaultOffet) {
    for (Window taskWindow : windows) {
      if (taskWindow.getLine() == startLine && taskWindow.getStart() > startOffset) {
        taskWindow.setStart(defaultOffet + (taskWindow.getStart() - startOffset));
      }
    }
  }

  private int getLineNumByOffset(Editor editor, int offset) {
    Document document = editor.getDocument();
    int lineCount = document.getLineCount();
    for (int i = 0; i < lineCount; i++) {
      if (offset >= document.getLineStartOffset(i) && offset < document.getLineStartOffset(i + 1)) {
        return i;
      }
    }
    if (offset > document.getTextLength()) {
      return -1;
    }
    return lineCount - 1;
  }

  public void updateOffsets(Editor selectedEditor) {
    if (mySelectedWindow != null) {
      RangeHighlighter selectedRangeHighlighter = mySelectedWindow.getRangeHighlighter();
      if (selectedRangeHighlighter != null) {
        int lineChange = selectedEditor.getDocument().getLineCount() - getLineNum();
        if (lineChange != 0) {
          int newStartLine = getLineNumByOffset(selectedEditor, selectedRangeHighlighter.getStartOffset());
          int newEndLine = getLineNumByOffset(selectedEditor, selectedRangeHighlighter.getEndOffset());
          increment(newStartLine, lineChange);
          mySelectedWindow.setLine(mySelectedWindow.getLine() - lineChange);
          setNewOffsetInLine(newEndLine, mySelectedWindow.getStart() + mySelectedWindow.getOffsetInLine(),
                             selectedRangeHighlighter.getEndOffset() - selectedEditor.getDocument().getLineStartOffset(newEndLine));
        }
        else {
          int oldEnd = mySelectedWindow.getRealStartOffset(selectedEditor) + mySelectedWindow.getOffsetInLine();
          int endChange = selectedRangeHighlighter.getEndOffset() - oldEnd;
          incrementAfterOffset(mySelectedWindow.getLine(), mySelectedWindow.getStart(), endChange);
        }
        int newLength = selectedRangeHighlighter.getEndOffset() - selectedRangeHighlighter.getStartOffset();
        mySelectedWindow.setOffsetInLine(newLength);
      }
    }
  }
}
