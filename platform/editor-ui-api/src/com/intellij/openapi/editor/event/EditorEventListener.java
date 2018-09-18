// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface EditorEventListener extends DocumentListener, CaretListener, SelectionListener {
  Topic<EditorEventListener> TOPIC = new Topic<>("changes in any currently open editor", EditorEventListener.class);

  default void readOnlyModificationAttempt(@NotNull Document document) {
  }
}
