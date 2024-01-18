// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  public JavaConfigurable(Project project) {}

  @Override
  public String getDisplayName() {
    return JavaBundle.message("java.configurable.display.name");
  }

  @Override
  public @NotNull String getId() {
    return JavaBundle.message("java.configurable.id");
  }

  @Override
  public @Nullable JComponent createComponent() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }
}
