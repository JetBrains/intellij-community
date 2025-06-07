// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*/
public class BuilderBasedTemplate implements ProjectTemplate {
  private final ModuleBuilder myBuilder;

  public BuilderBasedTemplate(ModuleBuilder builder) {
    myBuilder = builder;
  }

  @Override
  public String getId() {
    return myBuilder.getBuilderId();
  }

  @Override
  public @NotNull String getName() {
    return myBuilder.getPresentableName();
  }

  @Override
  public @Nullable String getDescription() {
    return myBuilder.getDescription();
  }

  @Override
  public Icon getIcon() {
    return myBuilder.getNodeIcon();
  }

  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return myBuilder;
  }

  @Override
  public @Nullable ValidationInfo validateSettings() {
    return null;
  }

  @Override
  public String toString() {
    return getName();
  }
}
