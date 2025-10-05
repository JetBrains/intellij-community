// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

/**
 * Same as {@link PsiDocumentTransactionListener}, but can be executed on a background thread.
 * This can improve performance and responsiveness of the IDE.
 */
@ApiStatus.Experimental
public interface PsiDocumentTransactionListenerBackgroundable extends PsiDocumentTransactionListener {

  @Topic.ProjectLevel
  @Topic.AppLevel
  Topic<PsiDocumentTransactionListenerBackgroundable> TOPIC =
    new Topic<>("psi.DocumentTransactionListenerBackgroundable", PsiDocumentTransactionListenerBackgroundable.class,
                Topic.BroadcastDirection.TO_PARENT);
}
