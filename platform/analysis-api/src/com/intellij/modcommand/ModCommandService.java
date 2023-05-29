// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModCommandService {
  @NotNull IntentionAction wrap(@NotNull ModCommandAction action);
  
  @Nullable ModCommandAction unwrap(@NotNull IntentionAction action);

  @NotNull ModStatus execute(@NotNull Project project, @NotNull ModCommand command);
}
