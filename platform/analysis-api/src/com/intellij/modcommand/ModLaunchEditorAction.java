// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A command to launch an interactive editor action in the context of an opened editor/file.
 *
 * @param actionId action identifier; taken from the IntelliJ IDEA action system (see {@code com.intellij.openapi.actionSystem.IdeActions}).
 *                 For example, "CodeCompletion" will invoke the code completion at the caret position.
 * @param optional if true, the action is optional and can be skipped if the ModCommand is executed non-interactively,
 *                 or if the action is not supported by the target editor. 
 *                 If false and it's unable to execute this action, an error should be displayed. 
 */
public record ModLaunchEditorAction(@NotNull @NonNls String actionId, boolean optional) implements ModCommand {
  /**
   * Action ID for invoking code completion at the caret position.
   */
  public static final String ACTION_CODE_COMPLETION = "CodeCompletion";
  
  /**
   * Action ID to show parameter info.
   */
  public static final String ACTION_PARAMETER_INFO = "ParameterInfo";
}
