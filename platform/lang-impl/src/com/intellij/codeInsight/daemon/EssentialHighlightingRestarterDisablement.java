// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Disables essential highlighting restart on save on a project basis
 */
public interface EssentialHighlightingRestarterDisablement {

  boolean shouldBeDisabledForProject(@NotNull Project project);
}
