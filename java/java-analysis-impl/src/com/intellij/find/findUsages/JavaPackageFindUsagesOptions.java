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
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaPackageFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isClassesUsages;
  public boolean isIncludeSubpackages = true;
  public boolean isSkipPackageStatements;

  public JavaPackageFindUsagesOptions(@NotNull Project project) {
    super(project);
  }

  public JavaPackageFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);
  }

  @Override
  protected void setDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    super.setDefaults(properties, prefix);
    isClassesUsages = properties.getBoolean(prefix + "isClassesUsages");
    isIncludeSubpackages = properties.getBoolean(prefix + "isIncludeSubpackages", true);
    isSkipPackageStatements = properties.getBoolean(prefix + "isSkipPackageStatements");
  }

  @Override
  protected void storeDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    super.storeDefaults(properties, prefix);
    properties.setValue(prefix + "isClassesUsages", isClassesUsages);
    properties.setValue(prefix + "isIncludeSubpackages", isIncludeSubpackages, true);
    properties.setValue(prefix + "isSkipPackageStatements", isSkipPackageStatements);
  }

  @Override
  protected void addUsageTypes(@NotNull List<? super String> to) {
    if (this.isUsages || this.isClassesUsages) {
      to.add(AnalysisBundle.message("find.usages.panel.title.usages"));
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (getClass() != o.getClass()) return false;

    JavaPackageFindUsagesOptions that = (JavaPackageFindUsagesOptions)o;

    if (isClassesUsages != that.isClassesUsages) return false;
    if (isIncludeSubpackages != that.isIncludeSubpackages) return false;
    if (isSkipPackageStatements != that.isSkipPackageStatements) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isClassesUsages ? 1 : 0);
    result = 31 * result + (isIncludeSubpackages ? 1 : 0);
    result = 31 * result + (isSkipPackageStatements ? 1 : 0);
    return result;
  }

}
