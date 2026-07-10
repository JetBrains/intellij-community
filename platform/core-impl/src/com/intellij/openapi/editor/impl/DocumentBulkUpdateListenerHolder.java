// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;

/**
 * Lazily creates the deprecated bulk-update topic publisher only when a document enters or leaves bulk mode.
 * <p>
 * New code should use {@link com.intellij.openapi.editor.event.DocumentListener#bulkUpdateStarting} and
 * {@link com.intellij.openapi.editor.event.DocumentListener#bulkUpdateFinished}; this holder keeps the old message-bus notification
 * alive for compatibility and keeps the deprecation suppression in one place.
 */
final class DocumentBulkUpdateListenerHolder {
  @SuppressWarnings("deprecation")
  static final DocumentBulkUpdateListener BULK_CHANGE_PUBLISHER = ApplicationManager.getApplication()
    .getMessageBus()
    .syncPublisher(DocumentBulkUpdateListener.TOPIC);
}
