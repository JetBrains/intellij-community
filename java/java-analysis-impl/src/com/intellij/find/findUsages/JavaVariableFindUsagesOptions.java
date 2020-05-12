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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaVariableFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isReadAccess = true;
  public boolean isWriteAccess = true;

  public JavaVariableFindUsagesOptions(@NotNull Project project) {
    super(project);
    isSearchForTextOccurrences = false;
  }

  public JavaVariableFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);
    isSearchForTextOccurrences = false;
  }

  @Override
  protected void setDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    // overrides default values from superclass
    isSearchForTextOccurrences = properties.getBoolean(prefix + "isSearchForTextOccurrences");
    isUsages = properties.getBoolean(prefix + "isUsages", true);
    isReadAccess = properties.getBoolean(prefix + "isReadAccess", true);
    isWriteAccess = properties.getBoolean(prefix + "isWriteAccess", true);
  }

  @Override
  protected void storeDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    // overrides default values from superclass
    properties.setValue(prefix + "isSearchForTextOccurrences", isSearchForTextOccurrences);
    properties.setValue(prefix + "isUsages", isUsages, true);
    properties.setValue(prefix + "isReadAccess", isReadAccess, true);
    properties.setValue(prefix + "isWriteAccess", isWriteAccess, true);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (getClass() != o.getClass()) return false;

    JavaVariableFindUsagesOptions that = (JavaVariableFindUsagesOptions)o;

    if (isReadAccess != that.isReadAccess) return false;
    if (isWriteAccess != that.isWriteAccess) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isReadAccess ? 1 : 0);
    result = 31 * result + (isWriteAccess ? 1 : 0);
    return result;
  }

}
