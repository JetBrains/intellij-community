package org.jetbrains.jps.server;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/4/11
 */
public class GlobalLibrary {

  private final String myName;
  private final List<String> myPaths;

  public GlobalLibrary(String name, List<String> paths) {
    myName = name;
    myPaths = paths;
  }

  public String getName() {
    return myName;
  }

  public List<String> getPaths() {
    return myPaths;
  }
}
