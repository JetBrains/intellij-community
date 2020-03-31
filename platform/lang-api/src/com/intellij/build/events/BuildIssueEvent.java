// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.build.issue.BuildIssue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface BuildIssueEvent extends MessageEvent {
  @NotNull
  BuildIssue getIssue();
}
