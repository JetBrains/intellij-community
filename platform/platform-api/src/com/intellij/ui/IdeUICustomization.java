// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
  public String getProjectConceptName() {
    return "project";
  }

  /**
   * Returns the name to be displayed in the UI for the "Project" concept (Rider changes this to "Solution").
   */
  public String getProjectDisplayName() {
    return StringUtil.capitalize(getProjectConceptName());
  }

  /**
   * Returns the name of the "Close Project" action (with mnemonic if needed).
   */
  public String getCloseProjectActionText() {
    return IdeBundle.message("action.close.project");
  }

  /**
   * Returns the title of the Project view toolwindow.
   */
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
    return "Insert selected suggestion by pressing space, dot, or other context-dependent keys";
  }

  public void customizeSchemePanel(@NotNull JPanel abstractSchemePanel, @NotNull JPanel verticalContainer) {  }
}
