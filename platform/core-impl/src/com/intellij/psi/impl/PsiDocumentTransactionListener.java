// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface PsiDocumentTransactionListener {
  Topic<PsiDocumentTransactionListener> TOPIC =
    new Topic<>("psi.DocumentTransactionListener", PsiDocumentTransactionListener.class, Topic.BroadcastDirection.TO_PARENT);

  void transactionStarted(@NotNull Document document, @NotNull PsiFile file);

  default void transactionCompleted(@NotNull Document document, @NotNull PsiFile file) {
  }
}
