// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.runConfiguration;

import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;

public final class JpsApplicationRunConfigurationType extends JpsElementTypeBase<JpsApplicationRunConfigurationProperties> implements JpsRunConfigurationType<JpsApplicationRunConfigurationProperties> {
  public static final JpsApplicationRunConfigurationType INSTANCE = new JpsApplicationRunConfigurationType();

  private JpsApplicationRunConfigurationType() {
  }
}
