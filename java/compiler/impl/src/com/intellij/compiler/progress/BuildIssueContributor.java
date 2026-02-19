// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.progress;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Experimental
public interface BuildIssueContributor {

  @Nullable
  BuildIssue createBuildIssue(@NotNull Project project,
                              @NotNull Collection<String> moduleNames,
                              @NotNull String title,
                              @NotNull String message,
                              @NotNull MessageEvent.Kind kind,
                              @Nullable VirtualFile virtualFile,
                              @Nullable Navigatable navigatable);
}
