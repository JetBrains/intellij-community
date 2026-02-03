// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.editor.Document;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EncodingManagerListener {
  @Topic.AppLevel
  Topic<EncodingManagerListener> ENCODING_MANAGER_CHANGES = new Topic<>(EncodingManagerListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  void propertyChanged(@Nullable Document document, @NotNull String propertyName, final Object oldValue, final Object newValue);
}
