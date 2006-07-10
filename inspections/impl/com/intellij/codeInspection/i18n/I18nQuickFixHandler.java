package com.intellij.codeInspection.i18n;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.properties.psi.PropertiesFile;

import java.util.Collection;

/**
 * @author Alexey
 */
public interface I18nQuickFixHandler {
  void checkApplicability(final PsiFile psiFile,
                          final Editor editor) throws IncorrectOperationException;
  void performI18nization(final PsiFile psiFile,
                          final Editor editor,
                          PsiLiteralExpression literalExpression,
                          Collection<PropertiesFile> propertiesFiles,
                          String key,
                          String value,
                          String i18nizedText) throws IncorrectOperationException;

  I18nizeQuickFixDialog createDialog(PsiFile psiFile, Editor editor, Project project);
}
