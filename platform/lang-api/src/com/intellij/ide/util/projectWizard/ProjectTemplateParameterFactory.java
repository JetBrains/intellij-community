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

  public abstract String getParameterId();

  /** Null if no UI needed */
  @Nullable
  public abstract WizardInputField createField(String defaultValue);

  @Nullable
  public abstract String detectParameterValue(Project project);

  public void applyResult(String value, ModifiableRootModel model) {}
}
