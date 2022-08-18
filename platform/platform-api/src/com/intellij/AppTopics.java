// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.util.messages.Topic;

public final class AppTopics {
  /**
   * Document load, save and reload events.
   */
  @Topic.AppLevel
  public static final Topic<FileDocumentManagerListener> FILE_DOCUMENT_SYNC = new Topic<>(FileDocumentManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);
}