/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class CompletionContributor extends AbstractCompletionContributor<CompletionParameters>{
  public static final ExtensionPointName<CompletionContributor> EP_NAME = ExtensionPointName.create("com.intellij.completion.contributor");

  /**
   * Invoked before completion is started. Is used mainly for determining custom offsets in editor, and to change default dummy identifier.
   * @param context
   */
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
  }

  /**
   * @param parameters
   * @return text to be shown at the bottom of lookup list
   */
  @Nullable
  public String advertise(@NotNull CompletionParameters parameters) {
    return null;
  }

  /**
   *
   * @param parameters
   * @param editor
   * @return hint text to be shown if no variants are found, typically "No suggestions"
   */
  @Nullable
  public String handleEmptyLookup(@NotNull CompletionParameters parameters, final Editor editor) {
    return null;
  }

  /**
   * @param actionId
   * @return String representation of action shortcut. Useful while advertising something
   * @see #advertise(CompletionParameters)
   */
  protected static String getActionShortcut(@NonNls final String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(actionId));
  }

}
