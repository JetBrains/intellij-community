package org.jetbrains.jps;

import java.util.LinkedHashMap;
import java.util.Map;

public class BytecodeTargetConfiguration {
  private String projectBytecodeTarget;
  private Map<String, String> modulesBytecodeTarget = new LinkedHashMap<String, String>();

  public String getProjectBytecodeTarget() {
    return projectBytecodeTarget;
  }

  public void setProjectBytecodeTarget(String projectBytecodeTarget) {
    this.projectBytecodeTarget = projectBytecodeTarget;
  }

  public Map<String, String> getModulesBytecodeTarget() {
    return modulesBytecodeTarget;
  }

  public void setModulesBytecodeTarget(Map<String, String> modulesBytecodeTarget) {
    this.modulesBytecodeTarget = modulesBytecodeTarget;
  }
}
