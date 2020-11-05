// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.progress;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface BuildEventFilter {

  ExtensionPointName<BuildEventFilter> MESSAGE_PROGRESS_EP = ExtensionPointName.create("com.intellij.build.buildEventFilter");

  @Nullable
  BuildEvent filterMessage(@NotNull Project project,
                           @NotNull Object parentId,
                           @NotNull String title,
                           @NotNull String message,
                           @NotNull MessageEvent.Kind kind,
                           @Nullable Navigatable navigatable);

}
