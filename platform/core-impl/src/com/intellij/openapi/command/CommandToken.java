// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a command currently being executed by a command processor.
 *
 * @see CommandProcessorEx#startCommand(Project, String, Object, UndoConfirmationPolicy)
 * @author yole
 */
public interface CommandToken {
  @Nullable
  Project getProject();
}
