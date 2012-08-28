package org.jetbrains.jps.incremental;

import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;

/**
 * @author nik
 */
public class ModuleBuildTarget extends BuildTarget {
  private final String myModuleName;
  private final boolean myTests;

  public ModuleBuildTarget(String moduleName, JavaModuleBuildTargetType targetType) {
    super(targetType);
    myModuleName = moduleName;
    myTests = targetType.isTests();
  }

  public String getModuleName() {
    return myModuleName;
  }

  public boolean isTests() {
    return myTests;
  }

  @Override
  public String getId() {
    return myModuleName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ModuleBuildTarget target = (ModuleBuildTarget)o;
    return myTests == target.myTests && myModuleName.equals(target.myModuleName);
  }

  @Override
  public int hashCode() {
    return 31 * myModuleName.hashCode() + (myTests ? 1 : 0);
  }
}
