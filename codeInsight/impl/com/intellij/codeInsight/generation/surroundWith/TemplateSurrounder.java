package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
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

  public boolean isApplicableForFileType(FileType fileType) {
    final TemplateContext templateContext = myTemplate.getTemplateContext();

    if (templateContext.HTML && (fileType == StdFileTypes.XHTML || fileType == StdFileTypes.HTML)) return true;
    if (templateContext.JSP && (fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX)) return true;

    if (templateContext.XML) {
      return fileType == StdFileTypes.XML;
    }
    return false;
  }

  public boolean isApplicable(@NotNull PsiElement[] elements) {
    final FileType fileType = elements[0].getContainingFile().getFileType();
    return isApplicableForFileType(fileType);
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
                          elements[elements.length - 1].getTextRange().getEndOffset();

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
