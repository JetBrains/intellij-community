// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.ibm.icu.text.ListFormatter;
import com.intellij.DynamicBundle;
import com.intellij.analysis.AnalysisBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class JavaFindUsagesOptions extends PersistentFindUsagesOptions {
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
  public final void setDefaults(@NotNull Project project) {
    setDefaults(PropertiesComponent.getInstance(project), findPrefix());
  }

  protected void setDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    isSearchForTextOccurrences = properties.getBoolean(prefix + "isSearchForTextOccurrences", true);
    isUsages = properties.getBoolean(prefix + "isUsages", true);
  }

  @Override
  public final void storeDefaults(@NotNull Project project) {
    storeDefaults(PropertiesComponent.getInstance(project), findPrefix());
  }

  protected void storeDefaults(@NotNull PropertiesComponent properties, @NotNull String prefix) {
    properties.setValue(prefix + "isUsages", isUsages, true);
    properties.setValue(prefix + "isSearchForTextOccurrences", isSearchForTextOccurrences, true);
  }

  private @NotNull String findPrefix() {
    return getClass().getSimpleName() + ".";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (getClass() != o.getClass()) return false;

    return isSkipImportStatements == ((JavaFindUsagesOptions)o).isSkipImportStatements;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isSkipImportStatements ? 1 : 0);
    return result;
  }

  protected void addUsageTypes(@NotNull List<? super String> to) {
    if (isUsages) {
      to.add(AnalysisBundle.message("find.usages.panel.title.usages"));
    }
  }

  @Override
  public final @NotNull String generateUsagesString() {
    List<String> strings = new ArrayList<>();
    addUsageTypes(strings);
    if (strings.isEmpty()) {
      return AnalysisBundle.message("find.usages.panel.title.usages");
    }
    return formatOrList(strings);
  }
  private static @NotNull @Nls String formatOrList(Collection<?> list) {
    return ListFormatter.getInstance(DynamicBundle.getLocale(), ListFormatter.Type.OR, ListFormatter.Width.WIDE).format(list);
  }
}
