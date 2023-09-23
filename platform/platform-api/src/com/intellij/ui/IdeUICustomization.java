// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

/**
 * Allows to apply IDE-specific customizations to the terms used in platform UI features.
 */
public class IdeUICustomization {
  public static IdeUICustomization getInstance() {
    return ApplicationManager.getApplication().getService(IdeUICustomization.class);
  }

  /**
   * Returns a message which mentions 'project' concept.
   */
  public @NotNull @Nls String projectMessage(@NotNull @PropertyKey(resourceBundle = ProjectConceptBundle.BUNDLE) String key, Object @NotNull ... params) {
    return ProjectConceptBundle.message(key, params);
  }

  /**
   * Returns a message which mentions 'project' concept.
   */
  public @NotNull Supplier<@Nls String> projectMessagePointer(@NotNull @PropertyKey(resourceBundle = ProjectConceptBundle.BUNDLE) String key, Object @NotNull ... params) {
    return ProjectConceptBundle.messagePointer(key, params);
  }

  /**
   * Returns the title of the Project view toolwindow.
   */
  public @Nls String getProjectViewTitle(@NotNull Project project) {
    return projectMessage("toolwindow.title.project.view");
  }

  public @Nls String getSelectAutopopupByCharsText() {
    return IdeBundle.message("ui.customization.select.auto.popup.by.chars.text");
  }

  /**
   * Allows to replace the text of the given action (only for the actions/groups that support this mechanism)
   */
  public @Nullable @Nls String getActionText(@NotNull String actionId) {
    return null;
  }

  /**
   * Allows to replace the description of the given action (only for the actions/groups that support this mechanism)
   */
  public @Nullable @Nls String getActionDescription(@SuppressWarnings("unused") @NotNull String actionId) {
    return null;
  }

  public @Nullable String getUiThemeEditorSchemePath(@SuppressWarnings("unused") @NotNull String themeId, @Nullable String editorSchemePath) {
    return editorSchemePath;
  }

  /**
   * Returns the name of the Version Control tool window
   */
  public @NotNull String getVcsToolWindowName() {
    return UIBundle.message("tool.window.name.version.control");
  }
}

/**
 * This message bundle contains strings which somehow mention 'project' concept. Other IDEs may use a different term for that (e.g. Rider
 * use 'solution'). Don't use this class directly, use {@link IdeUICustomization#projectMessage} instead.
 */
final class ProjectConceptBundle {
  public static final @NonNls String BUNDLE = "messages.ProjectConceptBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ProjectConceptBundle.class, BUNDLE);

  private ProjectConceptBundle() {
  }

  static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}