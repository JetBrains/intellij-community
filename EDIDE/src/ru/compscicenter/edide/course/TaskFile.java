package ru.compscicenter.edide.course;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

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
}
