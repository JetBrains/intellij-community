// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl.runConfiguration;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationState;

public class JpsApplicationRunConfigurationPropertiesImpl extends JpsElementBase<JpsApplicationRunConfigurationPropertiesImpl> implements JpsApplicationRunConfigurationProperties {
  private final JpsApplicationRunConfigurationState myState;

  public JpsApplicationRunConfigurationPropertiesImpl(JpsApplicationRunConfigurationState state) {
    myState = state;
  }

  @Override
  public @NotNull JpsApplicationRunConfigurationPropertiesImpl createCopy() {
    return new JpsApplicationRunConfigurationPropertiesImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public String getMainClass() {
    return myState.MAIN_CLASS_NAME;
  }

  @Override
  public void setMainClass(String value) {
    myState.MAIN_CLASS_NAME = value;
  }
}
