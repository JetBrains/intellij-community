package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author MYakovlev
 * Date: Jul 1, 2002
 */
public abstract class EditorHighlighter{
  public static EditorHighlighter getInstance(Project project) {
    return project.getComponent(EditorHighlighter.class);
  }

  /** Do selection in Editor. This selection is removed automatically, then caret position is changed,
   *  focus is gained to Editor,
   *  or this method is called again.
   */
  public abstract void selectInEditor(VirtualFile file, int startOffset, int endOffset, boolean toSelectLine, boolean toUseNormalSelection);

}
