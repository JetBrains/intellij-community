// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.BuildEventsNls.Title;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.FileMessageEventResult;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
@Internal
public class FileMessageEventImpl extends MessageEventImpl implements FileMessageEvent {

  private final @NotNull FilePosition myFilePosition;

  @Internal
  public FileMessageEventImpl(
    @Nullable Object id,
    @Nullable Object parentId,
    @Nullable Long time,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull Kind kind,
    @Nullable @Title String group,
    @NotNull FilePosition filePosition
  ) {
    super(id, parentId, time, message, hint, description, kind, group, null);
    myFilePosition = filePosition;
  }

  /**
   * @deprecated Use {@link FileMessageEvent#builder} event builder instead.
   */
  @Deprecated
  public FileMessageEventImpl(
    @NotNull Object parentId,
    @NotNull Kind kind,
    @Nullable @Title String group,
    @NotNull @Message String message,
    @Nullable @Description String detailedMessage,
    @NotNull FilePosition filePosition
  ) {
    this(null, parentId, null, message, null, detailedMessage, kind, group, filePosition);
  }

  @Override
  public @NotNull FileMessageEventResult getResult() {
    return new FileMessageEventResult() {
      @Override
      public FilePosition getFilePosition() {
        return myFilePosition;
      }

      @Override
      public Kind getKind() {
        return FileMessageEventImpl.this.getKind();
      }

      @Override
      public @Nullable String getDetails() {
        return getDescription();
      }
    };
  }

  @Override
  public @NotNull FilePosition getFilePosition() {
    return myFilePosition;
  }

  @Override
  public @Nullable String getHint() {
    String hint = super.getHint();
    if (hint == null && myFilePosition.getStartLine() >= 0) {
      hint = ":" + (myFilePosition.getStartLine() + 1);
    }
    return hint;
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull Project project) {
    return new FileNavigatable(project, myFilePosition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    FileMessageEventImpl event = (FileMessageEventImpl)o;
    return Objects.equals(myFilePosition, event.myFilePosition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myFilePosition);
  }
}
