/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 19, 2002
 * Time: 8:21:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class PathManagerEx {
  @NonNls private static final String TESTDATA_DIRECTORY = "testData";
  @NonNls private static final String RT_DIRECTORY = "rt";

  private PathManagerEx() {
  }

  public static String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + TESTDATA_DIRECTORY;
  }
  public static String getLibRtPath() {
    return PathManager.getLibPath() + File.separatorChar + RT_DIRECTORY;
  }

  public static String getPluginTempPath () {
    String systemPath = PathManager.getSystemPath();

    return systemPath + File.separator + PathManager.PLUGINS_DIRECTORY;
  }

}
