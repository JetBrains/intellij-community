// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.problems;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

public final class DefaultProblemFileHighlightFilter implements Condition<VirtualFile> {
  private final Project myProject;

  public DefaultProblemFileHighlightFilter(Project project) {
    myProject = project;
  }

  @Override
  public boolean value(final VirtualFile file) {
    return FileIndexUtil.isJavaSourceFile(myProject, file)
      && !CompilerManager.getInstance(myProject).isExcludedFromCompilation(file);
  }
}
