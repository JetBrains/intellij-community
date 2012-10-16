package org.jetbrains.jps.model.java.runConfiguration;

import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsApplicationRunConfigurationProperties extends JpsElement {
  String getMainClass();
  void setMainClass(String value);
}
