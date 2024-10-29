// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.build.issue.BuildIssue;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public final class BuildIssueEventImpl extends AbstractBuildEvent implements BuildIssueEvent {
  private final BuildIssue myIssue;
  private final Kind myKind;

  public BuildIssueEventImpl(@NotNull Object parentId,
                             @NotNull BuildIssue buildIssue,
                             @NotNull Kind kind) {
    super(new Object(), parentId, System.currentTimeMillis(), buildIssue.getTitle());
    myIssue = buildIssue;
    myKind = kind;
  }

  @Override
  public @NotNull String getDescription() {
    return myIssue.getDescription();
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
  public MessageEventResult getResult() {
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
