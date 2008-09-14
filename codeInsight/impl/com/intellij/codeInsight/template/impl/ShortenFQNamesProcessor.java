package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class ShortenFQNamesProcessor implements TemplateOptionalProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.ShortenFQNamesProcessor");

  public void processText(final Project project, final Template template, final Document document, final RangeMarker templateRange,
                          final Editor editor) {
    if (!template.isToShortenLongNames()) return;

    try {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      assert file != null;
      javaStyle.shortenClassReferences(file, templateRange.getStartOffset(), templateRange.getEndOffset());
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names");
  }

  public boolean isEnabled(final Template template) {
    return template.isToShortenLongNames();
  }

  public void setEnabled(final Template template, final boolean value) {
    template.setToShortenLongNames(value);
  }
}
