// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Same as {@link PsiDocumentTransactionListener}, but can be executed on a background thread.
 * This can improve performance and responsiveness of the IDE.
 */
@ApiStatus.Experimental
public interface PsiDocumentTransactionListenerBackgroundable {

  @Topic.ProjectLevel
  @Topic.AppLevel
  Topic<PsiDocumentTransactionListenerBackgroundable> TOPIC =
    new Topic<>("psi.DocumentTransactionListenerBackgroundable", PsiDocumentTransactionListenerBackgroundable.class,
                Topic.BroadcastDirection.TO_PARENT);

  void transactionStarted(@NotNull Document document, @NotNull PsiFile psiFile);

  default void transactionCompleted(@NotNull Document document, @NotNull PsiFile psiFile) {
  }
}
