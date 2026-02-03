// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * An intention action that is used to fix the compilation error 
 * with the ability to apply to the current file in batch.
 * <p>
 * If the action is used as an inspection quick-fix or 
 * as a usual intention action, using this interface will have no effect.
 * <p>
 * The interface is obsolete now and may be deprecated in the future. 
 * To get the similar functionality, use {@link ModCommand} API instead. 
 * You can implement {@link ModCommandAction} and its {@link ModCommandAction#getPresentation(ActionContext)}
 * may use {@link Presentation#withFixAllOption(ModCommandAction)}.
 * </p>
 */
@ApiStatus.Obsolete
public interface IntentionActionWithFixAllOption extends IntentionActionWithOptions {
  @Override
  default @NotNull List<IntentionAction> getOptions() {
    return Collections.singletonList(new FixAllHighlightingProblems(this));
  }

  /**
   * Tests if the supplied action belongs to the same family as this action.
   * Default implementation assumes that actions from the same family 
   * must have the same runtime class. Could be overridden to provide more specific 
   * behavior. Must be symmetrical (x.belongsToMyFamily(y) == y.belongsToMyFamily(x)) 
   * 
   * @param action action to check
   * @return true if the action belongs to the same family as this action.
   */
  default boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    return action.getClass().equals(getClass());
  }

  /** The text for the "fix all" intention in the submenu */
  default @NotNull @IntentionName String getFixAllText() {
    return AnalysisBundle.message("intention.name.apply.all.fixes.in.file", getFamilyName());
  }
}
