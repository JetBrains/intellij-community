package com.intellij;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author yole
 */
public class JavaTestUtil {

  private JavaTestUtil() {
  }

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }
  public static String getRelativeJavaTestDataPath() {
    final String absolute = getJavaTestDataPath();
    return StringUtil.trimStart(absolute, PathManager.getHomePath());
  }

}
