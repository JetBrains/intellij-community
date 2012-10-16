package org.jetbrains.jps.model.java.runConfiguration;

import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;

/**
 * @author nik
 */
public class JpsApplicationRunConfigurationType extends JpsRunConfigurationType<JpsApplicationRunConfigurationProperties> {
  public static final JpsApplicationRunConfigurationType INSTANCE = new JpsApplicationRunConfigurationType();

  private JpsApplicationRunConfigurationType() {
  }
}
