/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;

public interface PsiDocumentTransactionListener {
  Topic<PsiDocumentTransactionListener> TOPIC = new Topic("psi.DocumentTransactionListener", PsiDocumentTransactionListener.class, Topic.BroadcastDirection.TO_PARENT);

  void transactionStarted(Document doc, PsiFile file);
  void transactionCompleted(Document doc, PsiFile file);
}