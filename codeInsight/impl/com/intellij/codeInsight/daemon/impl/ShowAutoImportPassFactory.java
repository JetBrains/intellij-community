/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 25.11.2006
 * Time: 15:39:27
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class ShowAutoImportPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ShowAutoImportPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{
      Pass.UPDATE_VISIBLE,
      Pass.UPDATE_ALL,
    }, null, false, -1);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShowAutoImportPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    return new ShowAutoImportPass(myProject, editor);
  }
}