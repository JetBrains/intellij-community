package com.intellij.diagnostic.logging;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsoleImpl extends LogConsoleBase {
  private final String myPath;

  public LogConsoleImpl(Project project, File file, long skippedContents, String title, final boolean buildInActions) {
    super(project, file, skippedContents, title, buildInActions);
    myPath = file.getAbsolutePath();
  }


  @Nullable
  public String getTooltip() {
    return myPath;
  }

  public String getPath() {
    return myPath;
  }

}
