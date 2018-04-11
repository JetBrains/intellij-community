// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Allows to apply IDE-specific customizations to the terms used in platform UI features.
 */
public class ProjectUICustomization {
  public static ProjectUICustomization getInstance() {
    return ServiceManager.getService(ProjectUICustomization.class);
  }

  /**
   * Returns the name to be displayed in the UI for the "project" concept (Rider changes this to "solution").
   */
  public String getProjectConceptName() {
    return "project";
  }

  public static String replaceProjectConceptName(String text) {
    return text.replace("project", getInstance().getProjectConceptName());
  }


  public static void replaceProjectConceptName(AbstractButton component) {
    component.setText(component.getText().replace("project", getInstance().getProjectConceptName()));
  }

  public static void replaceProjectConceptName(Border border) {
    if (border instanceof TitledBorder) {
      TitledBorder titledBorder = (TitledBorder)border;
      titledBorder.setTitle(titledBorder.getTitle().replace("project", getInstance().getProjectConceptName()));
    }
  }

  public static String replaceProjectConceptNameForAction(String actionName) {
    String name = getInstance().getProjectConceptName();
    if (name.equals("project")) {
      return actionName;
    }
    return actionName.replace("_", "").replace("project", name).replace("Project", StringUtil.capitalize(name));
  }
}
