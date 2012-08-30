package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

/**
 * @author nik
 */
public class JavaModuleBuildTargetType extends BuildTargetType {
  public static final JavaModuleBuildTargetType PRODUCTION = new JavaModuleBuildTargetType("java-production", false);
  public static final JavaModuleBuildTargetType TEST = new JavaModuleBuildTargetType("java-test", true);
  private boolean myTests;

  private JavaModuleBuildTargetType(String typeId, boolean tests) {
    super(typeId);
    myTests = tests;
  }

  @Override
  public BuildTarget createTarget(@NotNull String targetId) {
    return new ModuleBuildTarget(targetId, this);
  }

  public boolean isTests() {
    return myTests;
  }

  public static JavaModuleBuildTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }
}
