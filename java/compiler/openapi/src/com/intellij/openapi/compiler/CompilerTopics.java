// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import com.intellij.util.messages.Topic;

public final class CompilerTopics {
  /**
   * Project level.
   */
  @Topic.ProjectLevel
  public static final Topic<CompilationStatusListener> COMPILATION_STATUS = new Topic<>(CompilationStatusListener.class, Topic.BroadcastDirection.NONE);

  private CompilerTopics() {
  }
}
