// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface ExecutionActionSuppressor {
  ExtensionPointName<ExecutionActionSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.executionActionSuppressor");

  boolean isSuppressed(Project project);
}
