package ru.compscicenter.edide.course;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import ru.compscicenter.edide.TaskWindow;
import ru.compscicenter.edide.course.Window;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: lia
 * Date: 21.06.14
 * Time: 18:53
 */
public class TaskFile {
    private String name;
    private List<Window> windows;

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

  public void create(Project project, VirtualFile baseDir, String resourseRoot) throws IOException {
    String systemIndependentName = FileUtil.toSystemIndependentName(name);
    String systemIndependentResourceRootName = FileUtil.toSystemIndependentName(resourseRoot);
    final int index = systemIndependentName.lastIndexOf("/");
    if (index > 0) {
      systemIndependentName = systemIndependentName.substring(index + 1);
    }
    FileUtil.copy(new File(resourseRoot, name), new File(baseDir.getPath(), systemIndependentName));
  }

  public void drawAllWindows(Editor editor) {
    for (Window taskWindow: windows){
      taskWindow.draw(editor, false);
    }
  }

  public void drawWindowByPos(Editor editor, LogicalPosition pos) {
    Window taskWindow = getTaskWindowByPos(editor, pos);
    if (taskWindow == null) {
      return;
    }
    if (taskWindow.getResolveStatus()) {
      taskWindow.draw(editor, false);
    } else {
      taskWindow.draw(editor, true);
    }
    //TODO:return this field;
    //myLastLineNum = editor.getDocument().getLineCount();
  }

  private Window getTaskWindowByPos(Editor editor, LogicalPosition pos) {
    int line = pos.line;
    int column = pos.column;
    int realOffset = editor.getDocument().getLineStartOffset(line) + column;
    for (Window tw: windows) {
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

  public Element saveState() {
    Element fileElement = new Element("file");
    for (Window window:windows) {
      fileElement.addContent(window.saveState());
    }
    return fileElement;
  }
}
