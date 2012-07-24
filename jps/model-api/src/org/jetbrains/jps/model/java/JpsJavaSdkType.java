package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.library.JpsSdkType;

/**
 * @author nik
 */
public class JpsJavaSdkType extends JpsSdkType<JpsSdkProperties>  {
  public static final JpsJavaSdkType INSTANCE = new JpsJavaSdkType();

  public JpsSdkProperties createCopy(JpsSdkProperties properties) {
    return new JpsSdkProperties(properties);
  }

  public static String getJavaExecutable(JpsSdkProperties properties) {
    return properties.getHomePath() + "/bin/java";
  }
}
