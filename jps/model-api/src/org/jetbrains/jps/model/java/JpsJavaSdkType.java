package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

/**
 * @author nik
 */
public class JpsJavaSdkType extends JpsSdkType<JpsDummyElement>  {
  public static final JpsJavaSdkType INSTANCE = new JpsJavaSdkType();

  public static String getJavaExecutable(JpsSdk<?> sdk) {
    return sdk.getHomePath() + "/bin/java";
  }
}
