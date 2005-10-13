package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.TemplateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class TemplateSurrounder implements Surrounder {
  public TemplateSurrounder(final TemplateImpl template) {
    myTemplate = template;
  }

  private TemplateImpl myTemplate;

  public String getTemplateDescription() {
    return myTemplate.getDescription();
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return true;
  }

  @Nullable public TextRange surroundElements(@NotNull Project project,
                                              @NotNull Editor editor,
                                              @NotNull PsiElement[] elements) throws IncorrectOperationException {
    final int startOffset = elements[0].getTextRange().getStartOffset();
    final int endOffset = elements[elements.length - 1].getTextRange().getEndOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    editor.getSelectionModel().setSelection(startOffset, endOffset);
    final String text = editor.getDocument().getText().substring(startOffset, endOffset).trim();
    TemplateManager.getInstance(project).startTemplate(editor, text, myTemplate);
    return null;
  }
}
