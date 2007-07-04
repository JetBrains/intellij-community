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
  private PathManagerEx() {
  }

  @NonNls
  public static String getTestDataPath() {
    return PathManager.getHomePath() + File.separatorChar + "testData";
  }
  @NonNls
  public static String getLibRtPath() {
    return PathManager.getLibPath() + File.separatorChar + "rt";
  }

  public static String getPluginTempPath () {
    String systemPath = PathManager.getSystemPath();

    return systemPath + File.separator + PathManager.PLUGINS_DIRECTORY;
  }
}
