package org.jetbrains.jps.server;

import java.util.Map;

/**
* @author Eugene Zhuravlev
*         Date: 9/10/11
*/
public class BuildParameters {
  public BuildType buildType = BuildType.MAKE;
  public Map<String,String> pathVariables;
  public boolean useInProcessJavac = true;

  public BuildParameters() {
  }

  public BuildParameters(BuildType buildType) {
    this.buildType = buildType;
  }
}
