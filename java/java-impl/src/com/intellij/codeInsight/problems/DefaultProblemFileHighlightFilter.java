package com.intellij.codeInsight.problems;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

/**
* @author yole
*/
public class DefaultProblemFileHighlightFilter implements Condition<VirtualFile> {
  private final Project myProject;

  public DefaultProblemFileHighlightFilter(Project project) {
    myProject = project;
  }

  public boolean value(final VirtualFile file) {
    return FileIndexUtil.isJavaSourceFile(myProject, file)
      && !CompilerManager.getInstance(myProject).isExcludedFromCompilation(file);
  }
}
