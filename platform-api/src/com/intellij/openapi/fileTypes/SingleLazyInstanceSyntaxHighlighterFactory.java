/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class SingleLazyInstanceSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private SyntaxHighlighter myValue;

  @NotNull
  public final SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    if (myValue == null) {
      myValue = createHighlighter();
    }
    return myValue;
  }

  protected abstract @NotNull SyntaxHighlighter createHighlighter();
}