// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * Allows to apply IDE-specific customizations to the terms used in platform UI features.
 */
public class IdeUICustomization {
  public static IdeUICustomization getInstance() {
    return ServiceManager.getService(IdeUICustomization.class);
  }

  /**
   * @deprecated it's hard to properly localize 'project' term in the middle of a sentence; if you need to use a 'project' term,
   * put the whole message to ProjectConceptBundle.properties and refer to it via {@link #projectMessage} instead
   */
  @Deprecated
  @NotNull
  public String getProjectConceptName() {
    return "project";
  }

  /**
   * Returns a message which mentions 'project' concept. 
   */
  @NotNull
  public String projectMessage(@NotNull @PropertyKey(resourceBundle = ProjectConceptBundle.BUNDLE) String key, Object @NotNull ... params) {
    return ProjectConceptBundle.message(key, params);
  }

  /**
   * @deprecated use {@code projectMessage("tab.title.project")} instead
   */
  @Deprecated
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getProjectDisplayName() {
    return projectMessage("tab.title.project");
  }

  /**
   * @deprecated use {@code projectMessage("action.close.project.text")} instead
   */
  @Deprecated
  @Nls
  public String getCloseProjectActionText() {
    return projectMessage("action.close.project.text");
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
  @Deprecated
  public String getProjectViewSelectInTitle() {
    return projectMessage("select.in.item.project.view");
  }

  /**
   * @deprecated use {@code projectMessage("scope.name.non.project.files")} instead
   */
  @Deprecated
  public String getNonProjectFilesScopeTitle() {
    return projectMessage("scope.name.non.project.files");
  }

  public String getSelectAutopopupByCharsText() {
    return IdeBundle.message("ui.customization.select.auto.popup.by.chars.text");
  }

  /**
   * Allows to replace the text of the given action (only for the actions/groups that support this mechanism)
   */
  @Nullable
  public String getActionText(@NotNull String actionId) {
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
