/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class JavaMethodFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isOverridingMethods = false;
  public boolean isImplementingMethods = false;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited = false;
  public boolean isIncludeOverloadUsages = false;

  public JavaMethodFindUsagesOptions(@NotNull Project project) {
    super(project);
    isSearchForTextOccurrences = false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaMethodFindUsagesOptions that = (JavaMethodFindUsagesOptions)o;

    if (isCheckDeepInheritance != that.isCheckDeepInheritance) return false;
    if (isImplementingMethods != that.isImplementingMethods) return false;
    if (isIncludeInherited != that.isIncludeInherited) return false;
    if (isIncludeOverloadUsages != that.isIncludeOverloadUsages) return false;
    if (isOverridingMethods != that.isOverridingMethods) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isOverridingMethods ? 1 : 0);
    result = 31 * result + (isImplementingMethods ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    result = 31 * result + (isIncludeOverloadUsages ? 1 : 0);
    return result;
  }

  @Override
  protected void addUsageTypes(@NotNull LinkedHashSet<String> strings) {
    super.addUsageTypes(strings);
    if (isIncludeOverloadUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.overloaded.methods.usages"));
    }
    if (isImplementingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.methods"));
    }
    if (isOverridingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.overriding.methods"));
    }

  }
}
