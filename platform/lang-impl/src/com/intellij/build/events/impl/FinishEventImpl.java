/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@Internal
public class FinishEventImpl extends AbstractBuildEvent implements FinishEvent {

  private final @NotNull EventResult myResult;

  @Internal
  public FinishEventImpl(
    @NotNull Object startId,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull EventResult result
  ) {
    super(startId, parentId, time, message, hint, description);
    myResult = result;
  }

  /**
   * @deprecated Use {@link FinishEvent#builder} event builder instead.
   */
  @Deprecated
  public FinishEventImpl(
    @NotNull Object eventId,
    @Nullable Object parentId,
    long eventTime,
    @NotNull @Message String message,
    @NotNull EventResult result
  ) {
    this(eventId, parentId, eventTime, message, null, null, result);
  }

  @Override
  public @Nullable String getHint() {
    if (super.getHint() != null) {
      return super.getHint();
    }
    if (myResult instanceof SuccessResult successResult && successResult.isUpToDate()) {
      return LangBundle.message("build.event.message.up.to.date");
    }
    return null;
  }

  @Override
  public @NotNull EventResult getResult() {
    return myResult;
  }
}
