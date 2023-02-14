// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CompilerConfigurable implements SearchableConfigurable.Parent, Configurable.NoScroll {
  static final String CONFIGURABLE_ID = "project.propCompiler";

  private final CompilerUIConfigurable myCompilerUIConfigurable;

  public CompilerConfigurable(Project project) {
    myCompilerUIConfigurable = new CompilerUIConfigurable(project);
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("compiler.configurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "project.propCompiler";
  }

  @Override
  @NotNull
  public String getId() {
    return CONFIGURABLE_ID;
  }

  @Override
  public JComponent createComponent() {
    return myCompilerUIConfigurable.createComponent();
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public boolean isModified() {
    return myCompilerUIConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myCompilerUIConfigurable.apply();
  }

  @Override
  public void reset() {
    myCompilerUIConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myCompilerUIConfigurable.disposeUIResources();
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
    return new Configurable[0];
  }

  @NotNull CompilerUIConfigurable getCompilerUIConfigurable() {
    return myCompilerUIConfigurable;
  }
}
