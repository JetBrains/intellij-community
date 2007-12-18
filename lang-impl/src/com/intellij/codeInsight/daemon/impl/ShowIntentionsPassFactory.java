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
public class ShowIntentionsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ShowIntentionsPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{
      Pass.UPDATE_VISIBLE,
      Pass.UPDATE_ALL,
    }, null, false, Pass.POPUP_HINTS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShowIntentionsPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    return new ShowIntentionsPass(myProject, editor, -1, null);
  }
}