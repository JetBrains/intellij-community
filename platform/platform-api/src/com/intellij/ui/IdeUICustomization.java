// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.text.StringUtil;
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
   * Returns the name to be displayed in the UI for the "project" concept (Rider changes this to "solution").
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public String getProjectConceptName() {
    return "project";
  }

  @NotNull
  public String projectMessage(@NotNull @PropertyKey(resourceBundle = ProjectBundle.BUNDLE) String key) {
    return ProjectBundle.message(key);
  }

  /**
   * Returns the name to be displayed in the UI for the "Project" concept (Rider changes this to "Solution").
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getProjectDisplayName() {
    return StringUtil.capitalize(getProjectConceptName());
  }

  /**
   * Returns the name of the "Close Project" action (with mnemonic if needed).
   */
  @Nls
  public String getCloseProjectActionText() {
    return IdeBundle.message("action.close.project");
  }

  /**
   * Returns the title of the Project view toolwindow.
   */
  @Nls
  public String getProjectViewTitle() {
    return StringUtil.capitalize(getProjectConceptName());
  }

  /**
   * Returns the title of the Project view Select In target.
   */
  public String getProjectViewSelectInTitle() {
    return getProjectViewTitle() + " View";
  }
  /**
   * Returns the title of the "Non-Project Files" scope.
   */
  public String getNonProjectFilesScopeTitle() {
    return "Non-" + StringUtil.capitalize(getProjectConceptName()) + " Files";
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
