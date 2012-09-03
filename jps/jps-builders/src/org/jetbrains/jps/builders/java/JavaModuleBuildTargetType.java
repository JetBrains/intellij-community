package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.module.JpsModule;

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

  @Nullable
  @Override
  public BuildTarget createTarget(@NotNull String targetId, @NotNull ProjectDescriptor projectDescriptor) {
    JpsModule module = projectDescriptor.rootsIndex.getModuleByName(targetId);
    return module != null ? new ModuleBuildTarget(module, this) : null;
  }

  public boolean isTests() {
    return myTests;
  }

  public static JavaModuleBuildTargetType getInstance(boolean tests) {
    return tests ? TEST : PRODUCTION;
  }
}
