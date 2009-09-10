package com.intellij;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public class JavaTestUtil {
  @NonNls private static final String JAVA_TEST_DATA = "java/java-tests/testData";

  public static String getJavaTestDataPath() {
    final String homePath = PathManager.getHomePath();
    File dir = new File(homePath, "community/" + JAVA_TEST_DATA);
    if (dir.exists()) {
      return dir.getPath();
    }
    return new File(homePath, JAVA_TEST_DATA).getPath();
  }
}
