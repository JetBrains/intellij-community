package org.jetbrains.jps.server;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/4/11
 */
public class SdkLibrary extends GlobalLibrary{

  private final String myHomePath;

  public SdkLibrary(String name, String homePath, List<String> paths) {
    super(name, paths);
    myHomePath = homePath;
  }

  public String getHomePath() {
    return myHomePath;
  }
}
