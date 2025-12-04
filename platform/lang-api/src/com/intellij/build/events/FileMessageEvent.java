// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.build.FilePosition;
import com.intellij.build.eventBuilders.FileMessageEventBuilder;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface FileMessageEvent extends MessageEvent {

  @NotNull FilePosition getFilePosition();

  @Override
  @NotNull FileMessageEventResult getResult();

  @CheckReturnValue
  static @NotNull FileMessageEventBuilder builder(
    @NotNull @Message String message,
    @NotNull MessageEvent.Kind kind,
    @NotNull FilePosition filePosition
  ) {
    return BuildEvents.getInstance().fileMessage(message, kind, filePosition);
  }
}
