// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Vladislav.Soroka
 */
@Internal
public abstract class AbstractBuildEvent implements BuildEvent {

  private final @NotNull Object myId;
  private @Nullable Object myParentId;
  private final @NotNull Long myTime;
  private final @NotNull @Message String myMessage;
  private @Nullable @Hint String myHint;
  private @Nullable @Description String myDescription;

  @Internal
  protected AbstractBuildEvent(
    @Nullable Object id,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description
  ) {
    myId = notNull(id, () -> new Object());
    myParentId = parentId;
    myTime = notNull(time, () -> System.currentTimeMillis());
    myMessage = message;
    myHint = hint;
    myDescription = description;
  }

  /**
   * @deprecated Use instead {@link AbstractBuildEvent} constructor with hint and description parameters.
   */
  @Deprecated
  public AbstractBuildEvent(
    @NotNull Object eventId,
    @Nullable Object parentId,
    long time,
    @NotNull @Message String message
  ) {
    this(eventId, parentId, time, message, null, null);
  }

  @Override
  public @NotNull Object getId() {
    return myId;
  }

  @Override
  public @Nullable Object getParentId() {
    return myParentId;
  }

  public void setParentId(@Nullable Object parentId) {
    myParentId = parentId;
  }

  @Override
  public long getEventTime() {
    return myTime;
  }

  @Override
  public @NotNull @Message String getMessage() {
    return myMessage;
  }

  @Override
  public @Nullable String getHint() {
    return myHint;
  }

  public void setHint(@Nullable @Hint String hint) {
    myHint = hint;
  }

  @Override
  public @Nullable @Description String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable @Description String description) {
    myDescription = description;
  }

  @Override
  public @NonNls String toString() {
    return getClass().getSimpleName() + "{" +
           "myEventId=" + myId +
           ", myParentId=" + myParentId +
           ", myTime=" + myTime +
           ", myMessage='" + myMessage + '\'' +
           ", myHint='" + myHint + '\'' +
           ", myDescription='" + myDescription + '\'' +
           '}';
  }
}
