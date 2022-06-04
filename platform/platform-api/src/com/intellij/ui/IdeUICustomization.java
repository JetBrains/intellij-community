// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.*;

/**
 * Allows to apply IDE-specific customizations to the terms used in platform UI features.
 */
public class IdeUICustomization {
  public static IdeUICustomization getInstance() {
    return ApplicationManager.getApplication().getService(IdeUICustomization.class);
  }

  /**
   * @deprecated it's hard to properly localize 'project' term in the middle of a sentence; if you need to use a 'project' term,
   * put the whole message to ProjectConceptBundle.properties and refer to it via {@link #projectMessage} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public String getProjectConceptName() {
    return "project";
  }

  /**
   * Returns a message which mentions 'project' concept.
   */
  @NotNull
  public @Nls String projectMessage(@NotNull @PropertyKey(resourceBundle = ProjectConceptBundle.BUNDLE) String key, Object @NotNull ... params) {
    return ProjectConceptBundle.message(key, params);
  }

  /**
   * @deprecated use {@code projectMessage("tab.title.project")} instead
   */
  @Deprecated(forRemoval = true)
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getProjectDisplayName() {
    return projectMessage("tab.title.project");
  }

  /**
   * Returns the title of the Project view toolwindow.
   */
  @Nls
  public String getProjectViewTitle() {
    return projectMessage("toolwindow.title.project.view");
  }

  /**
   * @deprecated use {@code projectMessage("select.in.item.project.view")} instead
   */
  @Deprecated(forRemoval = true)
  public String getProjectViewSelectInTitle() {
    return projectMessage("select.in.item.project.view");
  }

  public @Nls String getSelectAutopopupByCharsText() {
    return IdeBundle.message("ui.customization.select.auto.popup.by.chars.text");
  }

  /**
   * Allows to replace the text of the given action (only for the actions/groups that support this mechanism)
   */
  @Nullable
  public @Nls String getActionText(@NotNull String actionId) {
    return null;
  }

  /**
   * Returns the name of the Version Control tool window
   */
  @NotNull
  public String getVcsToolWindowName() {
    return UIBundle.message("tool.window.name.version.control");
  }
}


/**
 * This message bundle contains strings which somehow mention 'project' concept. Other IDEs may use a different term for that (e.g. Rider
 * use 'solution'). Don't use this class directly, use {@link IdeUICustomization#projectMessage} instead.
 */
final class ProjectConceptBundle {
  @NonNls public static final String BUNDLE = "messages.ProjectConceptBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BUNDLE);

  private ProjectConceptBundle() {
  }

  static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(INSTANCE.getResourceBundle(ProjectConceptBundle.class.getClassLoader()), key, null, params);
  }
}