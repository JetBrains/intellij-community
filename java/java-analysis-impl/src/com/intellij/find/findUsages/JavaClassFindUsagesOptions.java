/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.analysis.AnalysisBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaClassFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isMethodsUsages;
  public boolean isFieldsUsages;
  public boolean isDerivedClasses;
  public boolean isImplementingClasses;
  public boolean isDerivedInterfaces;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited;

  public JavaClassFindUsagesOptions(@NotNull Project project) {
    super(project);
  }

  public JavaClassFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);
  }

  @Override
  protected void setDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    super.setDefaults(properties, prefix);
    isMethodsUsages = properties.getBoolean(prefix + "isMethodsUsages");
    isFieldsUsages = properties.getBoolean(prefix + "isFieldsUsages");
    isDerivedClasses = properties.getBoolean(prefix + "isDerivedClasses");
    isImplementingClasses = properties.getBoolean(prefix + "isImplementingClasses");
    isDerivedInterfaces = properties.getBoolean(prefix + "isDerivedInterfaces");
    isCheckDeepInheritance = properties.getBoolean(prefix + "isCheckDeepInheritance", true);
    isIncludeInherited = properties.getBoolean(prefix + "isIncludeInherited");
  }

  @Override
  protected void storeDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    super.storeDefaults(properties, prefix);
    properties.setValue(prefix + "isMethodsUsages", isMethodsUsages);
    properties.setValue(prefix + "isFieldsUsages", isFieldsUsages);
    properties.setValue(prefix + "isDerivedClasses", isDerivedClasses);
    properties.setValue(prefix + "isImplementingClasses", isImplementingClasses);
    properties.setValue(prefix + "isDerivedInterfaces", isDerivedInterfaces);
    properties.setValue(prefix + "isCheckDeepInheritance", isCheckDeepInheritance, true);
    properties.setValue(prefix + "isIncludeInherited", isIncludeInherited);
  }

  @Override
  protected void addUsageTypes(@NotNull List<? super String> strings) {
    if (isUsages || isMethodsUsages || isFieldsUsages) {
      strings.add(AnalysisBundle.message("find.usages.panel.title.usages"));
    }
    if (isDerivedClasses) {
      strings.add(JavaAnalysisBundle.message(strings.isEmpty() ?
                                             "find.usages.panel.title.derived.classes.cap" :
                                             "find.usages.panel.title.derived.classes"));
    }
    if (isImplementingClasses) {
      strings.add(JavaAnalysisBundle.message(strings.isEmpty() ?
                                             "find.usages.panel.title.implementing.classes.cap" :
                                             "find.usages.panel.title.implementing.classes"));
    }
    if (isDerivedInterfaces) {
      strings.add(JavaAnalysisBundle.message(strings.isEmpty() ?
                                             "find.usages.panel.title.derived.interfaces.cap" :
                                             "find.usages.panel.title.derived.interfaces"));
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (getClass() != o.getClass()) return false;

    JavaClassFindUsagesOptions that = (JavaClassFindUsagesOptions)o;

    if (isCheckDeepInheritance != that.isCheckDeepInheritance) return false;
    if (isDerivedClasses != that.isDerivedClasses) return false;
    if (isDerivedInterfaces != that.isDerivedInterfaces) return false;
    if (isFieldsUsages != that.isFieldsUsages) return false;
    if (isImplementingClasses != that.isImplementingClasses) return false;
    if (isIncludeInherited != that.isIncludeInherited) return false;
    if (isMethodsUsages != that.isMethodsUsages) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isMethodsUsages ? 1 : 0);
    result = 31 * result + (isFieldsUsages ? 1 : 0);
    result = 31 * result + (isDerivedClasses ? 1 : 0);
    result = 31 * result + (isImplementingClasses ? 1 : 0);
    result = 31 * result + (isDerivedInterfaces ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    return result;
  }

}
