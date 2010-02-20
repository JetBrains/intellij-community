/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.formatter;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;

public class FormattingDocumentModelImpl implements FormattingDocumentModel{

  private final Document myDocument;
  private final PsiFile myFile;
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.FormattingDocumentModelImpl");

  public FormattingDocumentModelImpl(final Document document, PsiFile file) {
    myDocument = document;
    myFile = file;
  }

  public static FormattingDocumentModelImpl createOn(PsiFile file) {
    Document document = getDocumentToBeUsedFor(file);
    if (document != null) {
      if (PsiDocumentManager.getInstance(file.getProject()).isUncommited(document)) {
        LOG.error("Document is uncommited");
      }
      if (!document.getText().equals(file.getText())) {
        LOG.error(
          "Document and psi file texts should be equal : \nDocument text:\n" + document.getText() + "\nFile text:\n" + file.getText());
      }
      return new FormattingDocumentModelImpl(document, file);
    }
    else {
      return new FormattingDocumentModelImpl(new DocumentImpl(file.getText()), file);
    }

  }

  private static Document getDocumentToBeUsedFor(final PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;
    if (PsiDocumentManager.getInstance(project).isUncommited(document)) return null;
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(file.getProject())).getSynchronizer();
    if (synchronizer.isDocumentAffectedByTransactions(document)) return null;

    return document;
  }

  public int getLineNumber(int offset) {
    LOG.assertTrue (offset <= myDocument.getTextLength());
    return myDocument.getLineNumber(offset);
  }

  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  public CharSequence getText(final TextRange textRange) {
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public int getTextLength() {
    return myDocument.getTextLength();
  }

  public Document getDocument() {
    return myDocument;
  }

  public PsiFile getFile() {
    return myFile;
  }
}
