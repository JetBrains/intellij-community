// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.build.eventBuilders.MessageEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.BuildEventsNls.Title;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface MessageEvent extends BuildEvent {
  enum Kind {
    ERROR, WARNING, INFO, STATISTICS, SIMPLE
  }

  @NotNull Kind getKind();

  @Title
  @NotNull String getGroup();

  @Nullable Navigatable getNavigatable(@NotNull Project project);

  @NotNull MessageEventResult getResult();

  @CheckReturnValue
  static @NotNull MessageEventBuilder builder(
    @NotNull @Message String message,
    @NotNull MessageEvent.Kind kind
  ) {
    return BuildEvents.getInstance().message(message, kind);
  }
}
