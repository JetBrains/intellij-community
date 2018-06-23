// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class JavaMethodFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isOverridingMethods;
  public boolean isImplementingMethods;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited;
  public boolean isIncludeOverloadUsages;
  public boolean isImplicitToString = true;

  public JavaMethodFindUsagesOptions(@NotNull Project project) {
    super(project);
    isSearchForTextOccurrences = false;
  }

  public JavaMethodFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);
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
    if (isImplicitToString != that.isImplicitToString) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isOverridingMethods ? 1 : 0);
    result = 31 * result + (isImplementingMethods ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    result = 31 * result + (isIncludeOverloadUsages ? 1 : 0);
    result = 31 * result + (isImplicitToString ? 1 : 0);
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
