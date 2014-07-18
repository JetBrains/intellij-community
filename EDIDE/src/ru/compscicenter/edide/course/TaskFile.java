package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: liana
 * Date: 21.06.14
 * Time: 18:53
 * Implementation of task file which contains task windows for student to type in and
 * which is visible to student in project view
 */
public class TaskFile {
  private static final String FILE_ELEMENT_NAME = "taskFile";
  private static final String NAME_ATTRIBUTE_NAME = "name";
  private static final String LINE_ATTRIBUTE_NAME = "myLineNum";
  private static final String INDEX_ATTRIBUTE_NAME = "myIndex";
  private String name;
  private List<Window> windows;
  private int myLineNum = -1;
  private Task myTask;
  private Window mySelectedWindow = null;
  private int myIndex = -1;


  /**
   * Saves task file state for serialization
   *
   * @return xml element with attributes and content typical for task file
   */
  public Element saveState() {
    Element taskFileElement = new Element(FILE_ELEMENT_NAME);
    taskFileElement.setAttribute(NAME_ATTRIBUTE_NAME, name);
    taskFileElement.setAttribute(LINE_ATTRIBUTE_NAME, String.valueOf(myLineNum));
    taskFileElement.setAttribute(INDEX_ATTRIBUTE_NAME, String.valueOf(myIndex));
    for (Window window : windows) {
      taskFileElement.addContent(window.saveState());
    }
    return taskFileElement;
  }

  /**
   * initializes task file after reopening of project or IDE restart
   *
   * @param taskFileElement xml element which contains information about task file
   */
  public void loadState(Element taskFileElement) {
    name = taskFileElement.getAttributeValue(NAME_ATTRIBUTE_NAME);
    try {
      myLineNum = taskFileElement.getAttribute(LINE_ATTRIBUTE_NAME).getIntValue();
      myIndex = taskFileElement.getAttribute(INDEX_ATTRIBUTE_NAME).getIntValue();
    }
    catch (DataConversionException e) {
      e.printStackTrace();
    }
    List<Element> windowElements = taskFileElement.getChildren();
    windows = new ArrayList<Window>(windowElements.size());
    for (Element windowElement : windowElements) {
      Window window = new Window();
      window.loadState(windowElement);
      windows.add(window);
    }
  }

  /**
   * @return if all the windows in task file are marked as resolved
   */
  public boolean isResolved() {
    for (Window window : windows) {
      if (!window.isResolveStatus()) {
        return false;
      }
    }
    return true;
  }

  public Task getTask() {
    return myTask;
  }

  public Window getSelectedWindow() {
    return mySelectedWindow;
  }

  /**
   * @param selectedWindow window from this task file to be set as selected
   */
  public void setSelectedWindow(Window selectedWindow) {
    if (selectedWindow.getTaskFile() == this) {
      mySelectedWindow = selectedWindow;
    }
    else {
      throw new IllegalArgumentException("Window may be set as selected only in task file which it belongs to");
    }
  }

  public String getName() {
    return name;
  }

  public List<Window> getWindows() {
    return windows;
  }

  /**
   * Creates task files in its task folder in project user created
   *
   * @param taskDir      project directory of task which task file belongs to
   * @param resourceRoot directory where original task file stored
   * @throws IOException
   */
  public void create(VirtualFile taskDir, File resourceRoot) throws IOException {
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    File resourceFile = new File(resourceRoot, name);
    File fileInProject = new File(taskDir.getPath(), systemIndependentName);
    FileUtil.copy(resourceFile, fileInProject);
  }

  public void drawAllWindows(Editor editor) {
    for (Window window : windows) {
      window.draw(editor, false, false);
    }
  }


  /**
   * @param pos position in editor
   * @return task window located in specified position or null if there is no task window in this position
   */
  @Nullable
  public Window getTaskWindow(Editor editor, LogicalPosition pos) {
    int line = pos.line;
    Document document = editor.getDocument();
    if (line >= document.getLineCount()) {
      return null;
    }
    int column = pos.column;
    int offset = document.getLineStartOffset(line) + column;
    for (Window tw : windows) {
      if (line == tw.getLine()) {
        int twStartOffset = tw.getRealStartOffset(editor);
        int twEndOffset = twStartOffset + tw.getLength();
        if (twStartOffset <= offset && offset <= twEndOffset) {
          return tw;
        }
      }
    }
    return null;
  }

  /**
   * Updates task window lines
   *
   * @param startLine lines greater than this line and including this line will be updated
   * @param change    change to be added to line numbers
   */
  public void incrementLines(int startLine, int change) {
    for (Window taskWindow : windows) {
      if (taskWindow.getLine() >= startLine) {
        taskWindow.setLine(taskWindow.getLine() + change);
      }
    }
  }

  /**
   * Initializes state of task file
   *
   * @param task task which task file belongs to
   */

  public void init(Task task) {
    myTask = task;
    for (Window window : windows) {
      window.init(this);
    }
    Collections.sort(windows);
    for (int i = 0; i < windows.size(); i++) {
      windows.get(i).setIndex(i);
    }
  }

  /**
   * @param index index of task file in list of task files of its task
   */
  public void setIndex(int index) {
    myIndex = index;
  }

  /**
   * Updates windows in specific line
   *
   * @param lineChange         change in line number
   * @param line               line to be updated
   * @param newEndOffsetInLine distance from line start to end of inserted fragment
   * @param oldEndOffsetInLine distance from line start to end of changed fragment
   */
  public void updateLine(int lineChange, int line, int newEndOffsetInLine, int oldEndOffsetInLine) {
    for (Window w : windows) {
      if ((w.getLine() == line) && (w.getStart() > newEndOffsetInLine)) {
        int distance = w.getStart() - oldEndOffsetInLine;
        w.setStart(distance + newEndOffsetInLine);
        w.setLine(line + lineChange);
      }
    }
  }
}
