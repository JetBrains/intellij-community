/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene Zhuravlev
 *         Date: May 23, 2006
 */
public interface SourcesFinder<Scope> {
  /**
   * Searches for source file within the deployedModules
   * @param relPath relative path of the source to be found (fetched from the class file)
   * @param project
   * @param scope a search scope
   */
  PsiFile findSourceFile(String relPath, Project project, Scope scope);
}
