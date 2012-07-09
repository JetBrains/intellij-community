package org.jetbrains.jps.model.library;

import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public class JpsSdkProperties extends JpsElementProperties {
  private final String myHomePath;
  private final String myVersionString;

  public JpsSdkProperties(String homePath, String versionString) {
    myHomePath = homePath;
    myVersionString = versionString;
  }

  public JpsSdkProperties(JpsSdkProperties properties) {
    myHomePath = properties.myHomePath;
    myVersionString = properties.myVersionString;
  }

  public String getHomePath() {
    return myHomePath;
  }

  public String getVersionString() {
    return myVersionString;
  }
}
