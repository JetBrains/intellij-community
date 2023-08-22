// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface LightEditServiceListener {
  @Topic.AppLevel
  Topic<LightEditServiceListener> TOPIC = new Topic<>(LightEditServiceListener.class, Topic.BroadcastDirection.NONE);

  default void lightEditWindowOpened(@NotNull Project project) {}

  default void lightEditWindowClosed(@NotNull Project project) {}
}
