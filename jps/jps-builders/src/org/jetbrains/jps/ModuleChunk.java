package org.jetbrains.jps;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.RealModuleBuildTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author max
 */
public class ModuleChunk {
  private static final NotNullFunction<JpsModule,String> GET_NAME = new NotNullFunction<JpsModule, String>() {
    @NotNull
    @Override
    public String fun(JpsModule dom) {
      return dom.getName();
    }
  };
  private Set<JpsModule> myModules;
  private final boolean myTests;
  private Set<RealModuleBuildTarget> myTargets;

  public ModuleChunk(Set<JpsModule> modules, boolean tests) {
    myModules = modules;
    myTests = tests;
    myTargets = new LinkedHashSet<RealModuleBuildTarget>();
    for (JpsModule module : modules) {
      myTargets.add(new RealModuleBuildTarget(module, JavaModuleBuildTargetType.getInstance(tests)));
    }
  }

  public String getName() {
    if (myModules.size() == 1) return myModules.iterator().next().getName();
    return "ModuleChunk(" + StringUtil.join(myModules, GET_NAME, ",") + ")";
  }

  public Set<JpsModule> getModules() {
    return myModules;
  }

  public boolean isTests() {
    return myTests;
  }

  public Set<RealModuleBuildTarget> getTargets() {
    return myTargets;
  }

  public String toString() {
    return getName();
  }

  public JpsProject getProject() {
    return representativeModule().getProject();
  }

  public JpsModule representativeModule() {
    return myModules.iterator().next();
  }
}
