// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.problems;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ProblemListener {
  Topic<ProblemListener> TOPIC = new Topic<>("ProblemListener", ProblemListener.class);

  default void problemsAppeared(@NotNull VirtualFile file) {
  }

  default void problemsChanged(@NotNull VirtualFile file) {
  }

  default void problemsDisappeared(@NotNull VirtualFile file) {
  }
}
