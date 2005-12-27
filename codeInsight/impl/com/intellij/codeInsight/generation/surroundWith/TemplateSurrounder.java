package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
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

  @Nullable public TextRange surroundElements(@NotNull final Project project,
                                              @NotNull final Editor editor,
                                              @NotNull PsiElement[] elements) throws IncorrectOperationException {
    final boolean languageWithWSSignificant = SurroundWithHandler.isLanguageWithWSSignificant(elements[0]);

    final int startOffset = languageWithWSSignificant ?
                            editor.getSelectionModel().getSelectionStart():
                            elements[0].getTextRange().getStartOffset();

    final int endOffset = languageWithWSSignificant ?
                          editor.getSelectionModel().getSelectionEnd():
                          elements[elements.length - 1].getTextRange().getStartOffset();

    editor.getCaretModel().moveToOffset(startOffset);
    editor.getSelectionModel().setSelection(startOffset, endOffset);
    String text = editor.getDocument().getText().substring(startOffset, endOffset);

    if (!languageWithWSSignificant) text = text.trim();

    final String text1 = text;

    final Runnable action = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, text1, myTemplate);
      }
    };

    if (languageWithWSSignificant) {
      PsiManager.getInstance(project).performActionWithFormatterDisabled(
        action
      );
    } else {
      action.run();
    }

    return null;
  }
}
