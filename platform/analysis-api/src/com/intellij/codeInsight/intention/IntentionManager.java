// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Manager for intentions. All intentions must be registered here.
 *
 * @see <a href="https://www.jetbrains.com/help/idea/intention-actions.html">IntelliJ documentation</a>
 * @see IntentionAction
 */
public abstract class IntentionManager {
  /**
   * Key to be used within {@link UserDataHolder} in order to check presence of explicit indication on if intentions sub-menu
   * should be shown.
   */
  public static final Key<Boolean> SHOW_INTENTION_OPTIONS_KEY = Key.create("SHOW_INTENTION_OPTIONS_KEY");

  public static IntentionManager getInstance() {
    return ApplicationManager.getApplication().getService(IntentionManager.class);
  }

  /**
   * Registers an intention action.
   *
   * @param action the intention action to register.
   */
  public abstract void addAction(@NotNull IntentionAction action);

  /**
   * Returns all registered intention actions.
   *
   * @return array of registered actions.
   */
  public abstract IntentionAction @NotNull [] getIntentionActions();

  /**
   * Returns all registered intention actions which are available now
   * (not disabled via Settings|Intentions or Alt-Enter|Disable intention quick fix)
   *
   * @return list of actions.
   */
  public abstract @NotNull List<IntentionAction> getAvailableIntentions();

  /**
   * Returns all registered intention actions which are available for passed languages
   * (not disabled via Settings|Intentions or Alt-Enter|Disable intention quick fix)
   *
   * @return list of actions.
   */
  public abstract @NotNull List<IntentionAction> getAvailableIntentions(Collection<String> languages);

  /**
   * @deprecated Please use {@code <intentionAction>} extension point instead
   */
  @Deprecated
  public abstract void registerIntentionAndMetaData(@NotNull IntentionAction action, String @NotNull ... category);

  public abstract void unregisterIntention(@NotNull IntentionAction intentionAction);

  /**
   * @return actions used as additional options for the given problem.
   * E.g. actions for suppress the problem via comment, javadoc or annotation,
   * and edit corresponding inspection settings.
   */
  public abstract @NotNull List<IntentionAction> getStandardIntentionOptions(@NotNull HighlightDisplayKey displayKey,
                                                                             @NotNull PsiElement context);

  /**
   * @return "Fix all '' inspections problems for a file" intention if toolWrapper is local inspection or simple global one
   */
  public abstract @Nullable IntentionAction createFixAllIntention(@NotNull InspectionToolWrapper<?, ?> toolWrapper,
                                                                  @NotNull IntentionAction action);

  /**
   * @return intention to start code cleanup on file
   */
  public abstract @NotNull IntentionAction createCleanupAllIntention();

  /**
   * @return options for cleanup intention {@link #createCleanupAllIntention()}
   * e.g. edit enabled cleanup inspections or starting cleanup on predefined scope
   */
  public abstract @NotNull List<IntentionAction> getCleanupIntentionOptions();

  /**
   * Wraps given action in a LocalQuickFix object.
   *
   * @param action action to convert.
   * @return quick fix instance.
   */
  public abstract @NotNull LocalQuickFix convertToFix(@NotNull IntentionAction action);
}
