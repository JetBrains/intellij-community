// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.build.issue.BuildIssue;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@Internal
public final class BuildIssueEventImpl extends AbstractBuildEvent implements BuildIssueEvent {

  private final @NotNull Kind myKind;
  private final @NotNull BuildIssue myIssue;

  @Internal
  public BuildIssueEventImpl(
    @Nullable Object id,
    @Nullable Object parentId,
    @Nullable Long time,
    @Nullable @BuildEventsNls.Hint String hint,
    @NotNull BuildIssue buildIssue,
    @NotNull Kind kind
  ) {
    super(id, parentId, time, buildIssue.getTitle(), hint, buildIssue.getDescription());
    myIssue = buildIssue;
    myKind = kind;
  }

  public BuildIssueEventImpl(
    @NotNull Object parentId,
    @NotNull BuildIssue buildIssue,
    @NotNull Kind kind
  ) {
    this(null, parentId, null, null, buildIssue, kind);
  }

  @Override
  public @NotNull String getDescription() {
    assert super.getDescription() != null;
    return super.getDescription();
  }

  @Override
  public @NotNull BuildIssue getIssue() {
    return myIssue;
  }

  @Override
  public @NotNull Kind getKind() {
    return myKind;
  }

  @Override
  public @NotNull String getGroup() {
    return LangBundle.message("build.event.title.build.issues");
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull Project project) {
    return myIssue.getNavigatable(project);
  }

  @Override
  public @NotNull MessageEventResult getResult() {
    return new MessageEventResult() {
      @Override
      public Kind getKind() {
        return myKind;
      }

      @Override
      public @NotNull String getDetails() {
        return myIssue.getDescription();
      }
    };
  }
}
