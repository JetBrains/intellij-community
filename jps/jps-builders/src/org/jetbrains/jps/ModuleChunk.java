/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author max
 */
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

  public String getPresentableShortName() {
    String name = myModules.iterator().next().getName();
    if (myModules.size() > 1) {
      name += " and " + (myModules.size() - 1) + " more";
      String fullName = getName();
      if (fullName.length() < name.length()) {
        name = fullName;
      }
    }
    if (containsTests()) {
      name = "tests of " + name;
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
