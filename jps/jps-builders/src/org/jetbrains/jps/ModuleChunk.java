package org.jetbrains.jps;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
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
  private final boolean myContainsTests;
  private Set<ModuleBuildTarget> myTargets;

  public ModuleChunk(Set<ModuleBuildTarget> targets) {
    boolean containsTests = false;
    myTargets = targets;
    myModules = new LinkedHashSet<JpsModule>();
    for (ModuleBuildTarget target : targets) {
      myModules.add(target.getModule());
      containsTests |= target.isTests();
    }
    myContainsTests = containsTests;
  }

  public String getName() {
    if (myModules.size() == 1) return myModules.iterator().next().getName();
    return StringUtil.join(myModules, GET_NAME, ",");
  }

  public Set<JpsModule> getModules() {
    return myModules;
  }

  public boolean containsTests() {
    return myContainsTests;
  }

  public Set<ModuleBuildTarget> getTargets() {
    return myTargets;
  }

  public String toString() {
    return getName();
  }

  public ModuleBuildTarget representativeTarget() {
    return myTargets.iterator().next();
  }
}
