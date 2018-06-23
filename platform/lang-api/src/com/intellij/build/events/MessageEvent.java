// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.events;

import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface MessageEvent extends BuildEvent {
  enum Kind {
    ERROR, WARNING, INFO, STATISTICS, SIMPLE
  }

  @NotNull
  Kind getKind();

  @NotNull
  String getGroup();

  @Nullable
  Navigatable getNavigatable(@NotNull Project project);

  MessageEventResult getResult();
}
