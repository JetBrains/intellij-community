package com.intellij.util;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;

/**
 * @author sergey.evdokimov
 */
public class CompletionContributorForTextField extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    if (!(file instanceof PsiPlainTextFile)) return;

    ApplicationManager.getApplication().assertReadAccessAllowed();

    TextFieldCompletionProvider field = file.getUserData(TextFieldCompletionProvider.COMPLETING_TEXT_FIELD_KEY);
    if (field == null) return;

    String text = file.getText();
    int offset = parameters.getOffset();

    String prefix = field.getPrefix(text.substring(0, Math.min(text.length(), offset)));

    CompletionResultSet activeResult;

    if (!result.getPrefixMatcher().getPrefix().equals(prefix)) {
      activeResult = result.withPrefixMatcher(prefix);
    }
    else {
      activeResult = result;
    }

    field.addCompletionVariants(text, offset, prefix, activeResult);
  }
}
