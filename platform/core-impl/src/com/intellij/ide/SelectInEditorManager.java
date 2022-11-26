// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class SelectInEditorManager {
  public static SelectInEditorManager getInstance(Project project) {
    return project.getService(SelectInEditorManager.class);
  }

  /** Do selection in Editor. This selection is removed automatically, then caret position is changed,
   *  focus is gained to Editor,
   *  or this method is called again.
   */
  public abstract void selectInEditor(VirtualFile file, int startOffset, int endOffset, boolean toSelectLine, boolean toUseNormalSelection);
}
