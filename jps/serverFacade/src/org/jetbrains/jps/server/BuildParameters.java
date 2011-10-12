package org.jetbrains.jps.server;

/**
* @author Eugene Zhuravlev
*         Date: 9/10/11
*/
public class BuildParameters {
  public BuildType buildType = BuildType.MAKE;
  public boolean useInProcessJavac = true;

  public BuildParameters() {
  }

  public BuildParameters(BuildType buildType) {
    this.buildType = buildType;
  }
}
