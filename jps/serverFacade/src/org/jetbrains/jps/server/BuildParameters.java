package org.jetbrains.jps.server;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene Zhuravlev
*         Date: 9/10/11
*/
public class BuildParameters {
  public BuildType buildType = BuildType.MAKE;
  public boolean useInProcessJavac = true;
  public final List<GlobalLibrary> globalLibraries = new ArrayList<GlobalLibrary>();

  public BuildParameters() {
  }

  public BuildParameters(BuildType buildType) {
    this.buildType = buildType;
  }
}
