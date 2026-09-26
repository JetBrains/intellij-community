// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.build.eventBuilders.BuildIssueEventBuilder;
import com.intellij.build.issue.BuildIssue;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@Experimental
public interface BuildIssueEvent extends MessageEvent {

  @Override
  @Nullable String getDescription();

  @NotNull BuildIssue getIssue();

  static @NotNull BuildIssueEventBuilder builder(
    @NotNull BuildIssue issue,
    @NotNull MessageEvent.Kind kind
  ) {
    return BuildEvents.getInstance().buildIssue(issue, kind);
  }
}
