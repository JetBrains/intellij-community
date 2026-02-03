// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectTemplateParameterFactory {

  public static final ExtensionPointName<ProjectTemplateParameterFactory> EP_NAME = ExtensionPointName.create("com.intellij.projectTemplateParameterFactory");

  // standard ids
  public static final String IJ_BASE_PACKAGE = "IJ_BASE_PACKAGE";
  public static final String IJ_PROJECT_NAME = "IJ_PROJECT_NAME";
  public static final String IJ_APPLICATION_SERVER = "IJ_APPLICATION_SERVER";
  public static final String IJ_LANGUAGE_LEVEL = "IJ_LANGUAGE_LEVEL";

  public abstract String getParameterId();

  /** Null if no UI needed */
  public abstract @Nullable WizardInputField createField(String defaultValue);

  public abstract @Nullable String detectParameterValue(Project project);

  /** If null, no UI will be shown */
  public String getImmediateValue() {
    return null;
  }

  public void applyResult(String value, ModifiableRootModel model) {}
}
