// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl.runConfiguration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.runConfiguration.JpsTypedRunConfiguration;

public final class JpsRunConfigurationImpl<P extends JpsElement> extends JpsNamedCompositeElementBase<JpsRunConfigurationImpl<P>> implements
                                                                                                                            JpsTypedRunConfiguration<P> {
  private final JpsRunConfigurationType<P> myType;
  
  public JpsRunConfigurationImpl(@NotNull String name, JpsRunConfigurationType<P> type, P properties) {
    super(name);
    myType = type;
    myContainer.setChild(myType.getPropertiesRole(), properties);
  }

  private JpsRunConfigurationImpl(JpsRunConfigurationImpl<P> original) {
    super(original);
    myType = original.myType;
  }

  @Override
  public @NotNull JpsRunConfigurationImpl<P> createCopy() {
    return new JpsRunConfigurationImpl<>(this);
  }

  @Override
  public @NotNull P getProperties() {
    return myContainer.getChild(myType.getPropertiesRole());
  }

  @Override
  public JpsRunConfigurationType<P> getType() {
    return myType;
  }
}
