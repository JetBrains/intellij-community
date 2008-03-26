/*
 * @author max
 */
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class PsiAwareTextEditorImpl extends TextEditorImpl {
  private TextEditorBackgroundHighlighter myBackgroundHighlighter;

  public PsiAwareTextEditorImpl(@NotNull final Project project, @NotNull final VirtualFile file, final TextEditorProvider provider) {
    super(project, file, provider);
  }

  protected TextEditorComponent createEditorComponent(final Project project, final VirtualFile file) {
    return new PsiAwareTextEditorComponent(project, file, this);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new TextEditorBackgroundHighlighter(myProject, getEditor());
    }
    return myBackgroundHighlighter;
  }

  private static class PsiAwareTextEditorComponent extends TextEditorComponent {
    private final Project myProject;
    private PsiAwareTextEditorComponent(@NotNull final Project project,
                                        @NotNull final VirtualFile file,
                                        @NotNull final TextEditorImpl textEditor) {
      super(project, file, textEditor);
      myProject = project;
    }

    public Object getData(final String dataId) {
      if (DataConstants.DOMINANT_HINT_AREA_RECTANGLE.equals(dataId)) {
        final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(myProject).getActiveLookup();
        if (lookup != null && lookup.isVisible()) {
          return lookup.getBounds();
        }
      }
      return super.getData(dataId);
    }
  }
}