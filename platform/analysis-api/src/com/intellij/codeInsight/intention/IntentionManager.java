// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manager for intentions. All intentions must be registered here.
 *
 * @see IntentionAction
 */
public abstract class IntentionManager  {
  public static final ExtensionPointName<IntentionActionBean> EP_INTENTION_ACTIONS =
    new ExtensionPointName<>("com.intellij.intentionAction");

  /**
   * Key to be used within {@link UserDataHolder} in order to check presence of explicit indication on if intentions sub-menu
   * should be shown.
   */
  public static final Key<Boolean> SHOW_INTENTION_OPTIONS_KEY = Key.create("SHOW_INTENTION_OPTIONS_KEY");

  /**
   * @deprecated Use {@link #getInstance()} instead.
   * Returns instance of {@code IntentionManager} for given project.
   *
   * @param project the project for which the instance is returned.
   * @return instance of the {@code IntentionManager} assigned for given project.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static IntentionManager getInstance(Project project) {
    return getInstance();
  }

  public static IntentionManager getInstance() {
    return ServiceManager.getService(IntentionManager.class);
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
  @NotNull
  public abstract IntentionAction[] getIntentionActions();

  /**
   * Returns all registered intention actions which are available now
   * (not disabled via Settings|Intentions or Alt-Enter|Disable intention quick fix)
   *
   * @return array of actions.
   */
  @NotNull
  public abstract IntentionAction[] getAvailableIntentionActions();

  /**
   * Registers an intention action which can be enabled or disabled through the "Intention
   * Settings" dialog. To provide the description and the example code for the intention,
   * the directory with the name equal to {@link IntentionAction#getFamilyName()} needs to
   * be created under the {@code intentionDescriptions} directory of the resource root.
   * The directory needs to contain three files. {@code description.html} provides the
   * description of the intention, {@code before.java.template} provides the sample code
   * before the intention is invoked, and {@code after.java.template} provides the sample
   * code after invoking the intention. The templates can contain a fragment of code surrounded
   * with {@code <spot>} and {@code </spot>} markers. If present, that fragment
   * will be surrounded by a blinking rectangle in the inspection preview pane.
   *
   * @param action   the intention action to register.
   * @param category the name of the category or categories under which the intention will be shown
   *                 in the "Intention Settings" dialog.
   * @deprecated Please use {@code <intentionAction>} extension point instead
   */
  @Deprecated
  public abstract void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String... category);

  public abstract void unregisterIntention(@NotNull IntentionAction intentionAction);

  /**
   * @return actions used as additional options for the given problem.
   * E.g. actions for suppress the problem via comment, javadoc or annotation,
   * and edit corresponding inspection settings.
   */
  @NotNull
  public abstract List<IntentionAction> getStandardIntentionOptions(@NotNull HighlightDisplayKey displayKey, @NotNull PsiElement context);

  /**
   * @return "Fix all '' inspections problems for a file" intention if toolWrapper is local inspection or simple global one
   */
  @Nullable
  public abstract IntentionAction createFixAllIntention(@NotNull InspectionToolWrapper toolWrapper, @NotNull IntentionAction action);

  /**
   * @return intention to start code cleanup on file
   */
  @NotNull
  public abstract IntentionAction createCleanupAllIntention();

  /**
   * @return options for cleanup intention {@link #createCleanupAllIntention()}
   * e.g. edit enabled cleanup inspections or starting cleanup on predefined scope
   */
  @NotNull
  public abstract List<IntentionAction> getCleanupIntentionOptions();

  /**
   * Wraps given action in a LocalQuickFix object.
   * @param action action to convert.
   * @return quick fix instance.
   */
  @NotNull
  public abstract LocalQuickFix convertToFix(@NotNull IntentionAction action);
}
