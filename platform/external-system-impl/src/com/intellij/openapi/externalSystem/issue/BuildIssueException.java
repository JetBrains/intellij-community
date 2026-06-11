// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.issue;

import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueProvider;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public class BuildIssueException extends ExternalSystemException implements BuildIssueProvider {
  private final List<BuildIssue> myBuildIssues;

  public BuildIssueException(@NotNull BuildIssue issue) {
    this(List.of(issue));
  }

  public BuildIssueException(@NotNull List<BuildIssue> issues) {
    assert !issues.isEmpty() : "Build issue list must not be empty";
    super(issues.getFirst().getDescription(), getQuickfixIds(issues.getFirst()));
    myBuildIssues = List.copyOf(issues);
  }

  @Override
  public @NotNull BuildIssue getBuildIssue() {
    return myBuildIssues.getFirst();
  }

  public @NotNull List<BuildIssue> getBuildIssues() {
    return myBuildIssues;
  }

  private static String[] getQuickfixIds(@NotNull BuildIssue issue) {
    return issue.getQuickFixes().stream().map(fix -> fix.getId()).toArray(String[]::new);
  }
}
