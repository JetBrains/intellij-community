// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.util.messages.Topic;

public interface EditorMouseEventListener extends EditorMouseMotionListener, EditorMouseListener {
  Topic<EditorMouseEventListener> TOPIC = new Topic<>("mouse changes in any currently open editor", EditorMouseEventListener.class);
}
