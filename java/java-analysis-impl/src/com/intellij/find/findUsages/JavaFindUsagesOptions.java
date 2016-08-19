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

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public abstract class JavaFindUsagesOptions extends FindUsagesOptions {
  public boolean isSkipImportStatements;

  public JavaFindUsagesOptions(@NotNull Project project) {
    super(project);

    isUsages = true;
  }

  public JavaFindUsagesOptions(@NotNull SearchScope searchScope) {
    super(searchScope);

    isUsages = true;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    return isSkipImportStatements == ((JavaFindUsagesOptions)o).isSkipImportStatements;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isSkipImportStatements ? 1 : 0);
    return result;
  }

  protected void addUsageTypes(@NotNull LinkedHashSet<String> to) {
    if (isUsages) {
      to.add(FindBundle.message("find.usages.panel.title.usages"));
    }
  }

  @NotNull
  @Override
  public final String generateUsagesString() {
    String separator = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    LinkedHashSet<String> strings = new LinkedHashSet<>();
    addUsageTypes(strings);
    if (strings.isEmpty()) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    return StringUtil.join(strings, separator);
  }


}
