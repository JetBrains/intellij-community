// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.ide.util.PropertiesComponent;
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

  @Override
  protected void setDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    // overrides default values from superclass
    isSearchForTextOccurrences = properties.getBoolean(prefix + "isSearchForTextOccurrences");
    isUsages = properties.getBoolean(prefix + "isUsages", true);
    isOverridingMethods = properties.getBoolean(prefix + "isOverridingMethods");
    isImplementingMethods = properties.getBoolean(prefix + "isImplementingMethods");
    isCheckDeepInheritance = properties.getBoolean(prefix + "isCheckDeepInheritance", true);
    isIncludeInherited = properties.getBoolean(prefix + "isIncludeInherited");
    isIncludeOverloadUsages = properties.getBoolean(prefix + "isIncludeOverloadUsages");
    isImplicitToString = properties.getBoolean(prefix + "isImplicitToString", true);
  }

  @Override
  protected void storeDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    // overrides default values from superclass
    properties.setValue(prefix + "isSearchForTextOccurrences", isSearchForTextOccurrences);
    properties.setValue(prefix + "isUsages", isUsages, true);
    properties.setValue(prefix + "isOverridingMethods", isOverridingMethods);
    properties.setValue(prefix + "isImplementingMethods", isImplementingMethods);
    properties.setValue(prefix + "isCheckDeepInheritance", isCheckDeepInheritance, true);
    properties.setValue(prefix + "isIncludeInherited", isIncludeInherited);
    properties.setValue(prefix + "isIncludeOverloadUsages", isIncludeOverloadUsages);
    properties.setValue(prefix + "isImplicitToString", isImplicitToString, true);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (getClass() != o.getClass()) return false;

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
  protected void addUsageTypes(@NotNull LinkedHashSet<? super String> strings) {
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
