// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FormattingDocumentModelImpl implements FormattingDocumentModel {
  private final WhiteSpaceFormattingStrategy myWhiteSpaceStrategy;
  @NotNull private final Document myDocument;
  @NotNull private final PsiFile myFile;

  private static final Logger LOG = Logger.getInstance(FormattingDocumentModelImpl.class);
  private final CodeStyleSettings mySettings;

  public FormattingDocumentModelImpl(@NotNull final Document document, @NotNull PsiFile file) {
    myDocument = document;
    myFile = file;
    Language language = file.getLanguage();
    myWhiteSpaceStrategy = WhiteSpaceFormattingStrategyFactory.getStrategy(language);
    mySettings = CodeStyle.getSettings(file);
  }

  public static FormattingDocumentModelImpl createOn(@NotNull PsiFile file) {
    Document document = getDocumentToBeUsedFor(file);
    if (document != null) {
      checkDocument(file, document);
      return new FormattingDocumentModelImpl(document, file);
    }
    else {
      return new FormattingDocumentModelImpl(new DocumentImpl(file.getViewProvider().getContents(), true), file);
    }
  }

  private static void checkDocument(@NotNull PsiFile file, @NotNull Document document) {
    if (file.getTextLength() != document.getTextLength()) {
      LOG.error(DebugUtil.diagnosePsiDocumentInconsistency(file, document));
    }
  }

  @Nullable
  public static Document getDocumentToBeUsedFor(final PsiFile file) {
    final Project project = file.getProject();
    if (!file.isPhysical()) {
      return getDocumentForNonPhysicalFile(file);
    }
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      if (PsiDocumentManager.getInstance(project).isUncommited(document)) return null;
      PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).getSynchronizer();
      if (synchronizer.isDocumentAffectedByTransactions(document)) return null;
    }
    return document;
  }

  @NotNull
  private static Document getDocumentForNonPhysicalFile(PsiFile file) {
    Document document = file.getViewProvider().getDocument();
    if (document != null && document.getTextLength() == file.getTextLength()) {
      return document;
    }
    return new DocumentImpl(file.getText(), true);
  }

  @Override
  public int getLineNumber(int offset) {
    if (offset > myDocument.getTextLength()) {
      LOG.error(String.format("Invalid offset detected (%d). Document length: %d. Target file: %s",
                              offset, myDocument.getTextLength(), myFile));
    }
    return myDocument.getLineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  @Override
  public CharSequence getText(final TextRange textRange) {
    if (textRange.getStartOffset() < 0 || textRange.getEndOffset() > myDocument.getTextLength()) {
      LOG.error(String.format(
        "Please submit a ticket to the tracker and attach current source file to it!%nInvalid processing detected: given text "
        + "range (%s) targets non-existing regions (the boundaries are [0; %d)). File's language: %s",
        textRange, myDocument.getTextLength(), myFile.getLanguage()
      ));
    }
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public int getTextLength() {
    return myDocument.getTextLength();
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset) {
    Language startElementLanguage = getLanguageByOffset(startOffset);
    if (myWhiteSpaceStrategy.check(startElementLanguage, myDocument.getCharsSequence(), startOffset, endOffset) >= endOffset) {
      return true;
    }
    PsiElement injectedElement = InjectedLanguageUtilBase.findElementAtNoCommit(myFile, startOffset);
    if (injectedElement != null) {
      Language injectedLanguage = injectedElement.getLanguage();
      if (!injectedLanguage.equals(myFile.getLanguage())) {
        WhiteSpaceFormattingStrategy localStrategy = WhiteSpaceFormattingStrategyFactory.getStrategy(injectedLanguage);
        String unescapedText = InjectedLanguageUtilBase.getUnescapedLeafText(injectedElement, true);
        if (unescapedText != null) {
          return localStrategy.check(unescapedText, 0, unescapedText.length()) >= unescapedText.length();
        }

        return localStrategy.check(myDocument.getCharsSequence(), startOffset, endOffset) >= endOffset;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText, int startOffset, int endOffset,
                                                  ASTNode nodeAfter, boolean changedViaPsi)
  {
    if (!changedViaPsi) {
      return myWhiteSpaceStrategy.adjustWhiteSpaceIfNecessary(whiteSpaceText, myDocument.getCharsSequence(), startOffset, endOffset,
                                                              mySettings, nodeAfter);
    }

    final PsiElement element = myFile.findElementAt(startOffset);
    if (element == null) {
      return whiteSpaceText;
    }
    else {
      return myWhiteSpaceStrategy.adjustWhiteSpaceIfNecessary(whiteSpaceText, element, startOffset, endOffset, mySettings);
    }
  }

  public static boolean canUseDocumentModel(@NotNull Document document,@NotNull PsiFile file) {
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    return !psiDocumentManager.isUncommited(document) &&
           !psiDocumentManager.isDocumentBlockedByPsi(document) &&
           file.getText().equals(document.getText());
  }

  private Language getLanguageByOffset(int offset) {
    if (offset < myFile.getTextLength()) {
      PsiElement element = myFile.findElementAt(offset);
      if (element != null) {
        return element.getLanguage();
      }
    }
    return null;
  }
}
