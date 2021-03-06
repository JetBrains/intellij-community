// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.LinkedHashSet;
import java.util.Set;

public class ModuleChunk {
  private static final NotNullFunction<JpsModule,String> GET_NAME = dom -> dom.getName();
  private final Set<JpsModule> myModules;
  private final boolean myContainsTests;
  private final Set<ModuleBuildTarget> myTargets;

  public ModuleChunk(Set<ModuleBuildTarget> targets) {
    boolean containsTests = false;
    myTargets = targets;
    myModules = new LinkedHashSet<>();
    for (ModuleBuildTarget target : targets) {
      myModules.add(target.getModule());
      containsTests |= target.isTests();
    }
    myContainsTests = containsTests;
  }

  public @Nls String getPresentableShortName() {
    String first = myModules.iterator().next().getName();
    String name;
    if (myModules.size() > 1) {
      name = JpsBuildBundle.message("target.description.0.and.1.more", first, myModules.size() - 1);
      String fullName = getName();
      if (fullName.length() < name.length()) {
        name = fullName;
      }
    }
    else {
      name = first;
    }
    if (containsTests()) {
      return JpsBuildBundle.message("target.description.tests.of.0", name);
    }
    return name;
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
