package com.intellij.util;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

/**
 * @author sergey.evdokimov
 */
public abstract class TextFieldCompletionProvider {

  static final Key<TextFieldCompletionProvider> COMPLETING_TEXT_FIELD_KEY = Key.create("COMPLETING_TEXT_FIELD_KEY");

  public void apply(@NotNull EditorTextField field) {
    Project project = field.getProject();
    assert project != null;
    field.setDocument(createDocument(project));
  }

  private Document createDocument(final Project project) {
    final FileType fileType = PlainTextLanguage.INSTANCE.getAssociatedFileType();
    assert fileType != null;

    final long stamp = LocalTimeCounter.currentTime();
    final PsiFile psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("Dummy." + fileType.getDefaultExtension(), fileType, "", stamp, true, false);

    psiFile.putUserData(COMPLETING_TEXT_FIELD_KEY, this);

    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    assert document != null;
    return document;
  }

  @NotNull
  protected String getPrefix(@NotNull String currentTextPrefix) {
    return currentTextPrefix;
  }

  protected abstract void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result);
}
