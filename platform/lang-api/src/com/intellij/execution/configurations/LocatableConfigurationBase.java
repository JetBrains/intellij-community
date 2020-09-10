// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base class that should be used for configurations that can be created from context by a {@link com.intellij.execution.actions.RunConfigurationProducer}}.
 * It supports automatically generating a name for a configuration from its settings and keeping track of whether the name was changed by
 * the user.
 *
 * @author yole
 */
public abstract class LocatableConfigurationBase<T> extends RunConfigurationBase<T> implements LocatableConfiguration {
  protected LocatableConfigurationBase(@NotNull Project project, @NotNull ConfigurationFactory factory, @Nullable String name) {
    super(project, factory, name);
  }

  protected LocatableConfigurationBase(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(project, factory, null);
  }

  @NotNull
  @Override
  protected LocatableRunConfigurationOptions getOptions() {
    return (LocatableRunConfigurationOptions)super.getOptions();
  }

  @NotNull
  @Override
  protected Class<? extends LocatableRunConfigurationOptions> getDefaultOptionsClass() {
    return LocatableRunConfigurationOptions.class;
  }

  @Override
  @Attribute("nameIsGenerated")
  public boolean isGeneratedName() {
    return getOptions().isNameGenerated() && suggestedName() != null;
  }

  /**
   * Renames the configuration to its suggested name.
   */
  public void setGeneratedName() {
    setName(StringUtilRt.notNullize(suggestedName()));
    getOptions().setNameGenerated(true);
  }

  public void setNameChangedByUser(boolean nameChangedByUser) {
    getOptions().setNameGenerated(!nameChangedByUser);
  }

  /**
   * Returns the text of the context menu action to start this run configuration. This can be different from the run configuration name
   * (for example, for a Java unit test method, the context menu shows just the name of the method, whereas the name of the run
   * configuration includes the class name).
   *
   * @return the name of the action.
   */
  @Nullable
  public @NlsActions.ActionText String getActionName() {
    String name = getName();
    final String shortName = name.length() < 20 ? name : name.substring(0, 20) + "...";
    return shortName + getPresentableType();
  }
}
