// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface DocumentMarkupModelListener {

  @Topic.AppLevel
  Topic<DocumentMarkupModelListener> TOPIC = new Topic<>(DocumentMarkupModelListener.class);

  void markupModelCreated(@Nullable Project project, @NotNull MarkupModelEx markupModel);

  void markupModelDisposed(@Nullable Project project, @NotNull MarkupModelEx markupModel);
}
