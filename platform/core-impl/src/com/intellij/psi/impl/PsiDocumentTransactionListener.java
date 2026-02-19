// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface PsiDocumentTransactionListener {

  @Topic.ProjectLevel
  @Topic.AppLevel
  Topic<PsiDocumentTransactionListener> TOPIC =
    new Topic<>("psi.DocumentTransactionListener", PsiDocumentTransactionListener.class, Topic.BroadcastDirection.TO_PARENT);

  void transactionStarted(@NotNull Document document, @NotNull PsiFile psiFile);

  default void transactionCompleted(@NotNull Document document, @NotNull PsiFile psiFile) {
  }
}
