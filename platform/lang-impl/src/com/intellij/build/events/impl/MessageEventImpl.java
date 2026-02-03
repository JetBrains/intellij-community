// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.BuildEventsNls.Title;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Vladislav.Soroka
 */
@Internal
public class MessageEventImpl extends AbstractBuildEvent implements MessageEvent {

  private final @NotNull Kind myKind;
  private final @NotNull @Title String myGroup;
  private final @Nullable Navigatable myNavigatable;

  @Internal
  public MessageEventImpl(
    @Nullable Object id,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull Kind kind,
    @Nullable @Title String group,
    @Nullable Navigatable navigatable
  ) {
    super(id, parentId, time, message, hint, description);
    myKind = kind;
    myGroup = notNull(group, () -> LangBundle.message("build.event.title.other.messages"));
    myNavigatable = navigatable;
  }

  /**
   * @deprecated Use {@link MessageEvent#builder} event builder instead.
   */
  @Deprecated
  public MessageEventImpl(
    @NotNull Object parentId,
    @NotNull Kind kind,
    @Nullable @Title String group,
    @NotNull @Message String message,
    @Nullable @Description String detailedMessage
  ) {
    this(null, parentId, null, message, null, detailedMessage, kind, group, null);
  }

  @Override
  public final void setDescription(@Nullable @Description String description) {
    super.setDescription(description);
  }

  @Override
  public @NotNull Kind getKind() {
    return myKind;
  }

  @Override
  public @NotNull String getGroup() {
    return myGroup;
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull Project project) {
    return myNavigatable;
  }

  @Override
  public @NotNull MessageEventResult getResult() {
    return new MessageEventResult() {
      @Override
      public Kind getKind() {
        return myKind;
      }

      @Override
      public String getDetails() {
        return getDescription();
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MessageEventImpl event = (MessageEventImpl)o;
    return Objects.equals(getMessage(), event.getMessage()) &&
           Objects.equals(getDescription(), event.getDescription()) &&
           Objects.equals(myGroup, event.myGroup);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroup, getMessage());
  }
}
