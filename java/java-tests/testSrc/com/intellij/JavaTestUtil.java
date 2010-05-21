package com.intellij;

import com.intellij.openapi.application.ex.PathManagerEx;

/**
 * @author yole
 */
public class JavaTestUtil {

  private JavaTestUtil() {
  }

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }
}
