/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
   * @deprecated Use {@link #getInstance()} unstead.
   * Returns instance of <code>IntentionManager</code> for given project.
   *
   * @param project the project for which the instance is returned.
   * @return instance of the <code>IntentionManager</code> assigned for given project.
   */
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
   * be created under the <code>intentionDescriptions</code> directory of the resource root.
   * The directory needs to contain three files. <code>description.html</code> provides the
   * description of the intention, <code>before.java.template</code> provides the sample code
   * before the intention is invoked, and <code>after.java.template</code> provides the sample
   * code after invoking the intention. The templates can contain a fragment of code surrounded
   * with <code>&lt;spot&gt;</code> and <code>&lt;/spot&gt;</code> markers. If present, that fragment
   * will be surrounded by a blinking rectangle in the inspection preview pane.
   *
   * @param action   the intention action to register.
   * @param category the name of the category or categories under which the intention will be shown
   *                 in the "Intention Settings" dialog.
   */
  public abstract void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String... category);

  /**
   * @deprecated custom directory name causes problem with internationalization of intention descriptions.
   * Register intention class via extension point {@link IntentionManager#EP_INTENTION_ACTIONS} instead.
   */
  @Deprecated
  public abstract void registerIntentionAndMetaData(@NotNull IntentionAction action,
                                                    @NotNull String[] category,
                                                    @NotNull String descriptionDirectoryName);

  public abstract void registerIntentionAndMetaData(@NotNull IntentionAction action,
                                                    @NotNull String[] category,
                                                    @NotNull String description,
                                                    @NotNull String exampleFileExtension,
                                                    @NotNull String[] exampleTextBefore,
                                                    @NotNull String[] exampleTextAfter);

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
  public abstract IntentionAction createFixAllIntention(InspectionToolWrapper toolWrapper, IntentionAction action);

  /**
   * Wraps given action in a LocalQuickFix object.
   * @param action action to convert.
   * @return quick fix instance.
   */
  @NotNull
  public abstract LocalQuickFix convertToFix(@NotNull IntentionAction action);
}
