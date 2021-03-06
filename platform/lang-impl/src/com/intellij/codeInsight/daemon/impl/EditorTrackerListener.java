// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public interface EditorTrackerListener extends EventListener{
  @Topic.ProjectLevel
  Topic<EditorTrackerListener> TOPIC = new Topic<>(EditorTrackerListener.class, Topic.BroadcastDirection.NONE);

  void activeEditorsChanged(@NotNull List<? extends Editor> activeEditors);
}
