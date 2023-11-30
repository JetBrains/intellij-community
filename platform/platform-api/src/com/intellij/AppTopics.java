// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.util.messages.Topic;

/**
 * @deprecated don't add new constants to this class and use replacements for existing ones.
 */
@Deprecated
public final class AppTopics {
  /**
   * @deprecated use {@link FileDocumentManagerListener#TOPIC} instead
   */
  @Deprecated
  @Topic.AppLevel
  public static final Topic<FileDocumentManagerListener> FILE_DOCUMENT_SYNC = FileDocumentManagerListener.TOPIC;
}