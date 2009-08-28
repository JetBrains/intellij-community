package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
*/
public class ExternalToolPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ExternalToolPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    // start after PostHighlightingPass completion since it could report errors that can prevent us to run
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.POST_UPDATE_ALL}, null, true, Pass.EXTERNAL_TOOLS);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExternalToolPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.EXTERNAL_TOOLS);
    if (textRange == null || !externalAnnotatorsDefined(file)) {
      return null;
    }
    return new ExternalToolPass(file, editor, textRange.getStartOffset(), textRange.getEndOffset());
  }

  private static boolean externalAnnotatorsDefined(PsiFile file) {
    for (Language language : file.getViewProvider().getLanguages()) {
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.INSTANCE.allForLanguage(language);
      if (!externalAnnotators.isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
