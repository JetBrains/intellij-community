// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public final class LanguageLevelParameterFactory extends ProjectTemplateParameterFactory {
  @Override
  public String getParameterId() {
    return IJ_LANGUAGE_LEVEL;
  }

  @Nullable
  @Override
  public WizardInputField createField(String defaultValue) {
    return null;
  }

  @Override
  public String getImmediateValue() {
    return LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject()).getLanguageLevel().name();
  }

  @Nullable
  @Override
  public String detectParameterValue(Project project) {
    return null;
  }

  @Override
  public void applyResult(String value, ModifiableRootModel model) {
    try {
      boolean aDefault = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject()).isDefault();
      if (aDefault) {
        LanguageLevelProjectExtension.getInstance(model.getProject()).setDefault(true);
      }
      else {
        LanguageLevel level = LanguageLevel.valueOf(value);
        LanguageLevelProjectExtension.getInstance(model.getProject()).setLanguageLevel(level);
      }
    }
    catch (IllegalArgumentException ignore) {
    }
  }
}
