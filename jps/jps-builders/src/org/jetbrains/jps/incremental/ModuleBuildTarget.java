package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class ModuleBuildTarget extends BuildTarget {
  private final JpsModule myModule;
  private final String myModuleName;
  private final boolean myTests;

  public ModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
    super(targetType);
    myModuleName = module.getName();
    myTests = targetType.isTests();
    myModule = module;
  }

  @NotNull
  public JpsModule getModule() {
    return myModule;
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
    if (o == null || !(o instanceof ModuleBuildTarget)) {
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
