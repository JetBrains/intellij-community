// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.impl.runConfiguration.JpsRunConfigurationImpl;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfiguration;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;

import java.util.List;

@ApiStatus.Internal
public abstract class JpsProjectBase extends JpsRootElementBase<JpsProjectBase> implements JpsProject {
  private static final JpsElementCollectionRole<JpsRunConfiguration>
    RUN_CONFIGURATIONS_ROLE = JpsElementCollectionRole.create(JpsElementChildRoleBase.create("run configuration"));

  protected JpsProjectBase(@NotNull JpsModel model) { 
    super(model);
    myContainer.setChild(RUN_CONFIGURATIONS_ROLE);
  }

  @Override
  public @NotNull <P extends JpsElement> Iterable<JpsTypedRunConfiguration<P>> getRunConfigurations(JpsRunConfigurationType<P> type) {
    return getRunConfigurationCollection().getElementsOfType(type);
  }

  @Override
  public @NotNull List<JpsRunConfiguration> getRunConfigurations() {
    return getRunConfigurationCollection().getElements();
  }

  @Override
  public @NotNull <P extends JpsElement> JpsTypedRunConfiguration<P> addRunConfiguration(@NotNull String name,
                                                                                         @NotNull JpsRunConfigurationType<P> type,
                                                                                         @NotNull P properties) {
    return getRunConfigurationCollection().addChild(new JpsRunConfigurationImpl<>(name, type, properties));
  }

  private JpsElementCollection<JpsRunConfiguration> getRunConfigurationCollection() {
    return myContainer.getChild(RUN_CONFIGURATIONS_ROLE);
  }

  @Override
  public @NotNull JpsElementReference<JpsProject> createReference() {
    return new JpsProjectElementReference();
  }
}
