// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@State(name = "TraceSettings", storages = @Storage("debugger.xml"), category = SettingsCategory.TOOLS)
public class TraceSettings implements PersistentStateComponent<TraceSettings> {
  private ClassFilter[] myClassFilters;
  private ClassFilter[] myClassExclusionFilters;

  @XCollection(propertyElementName = "class-filters")
  public final ClassFilter[] getClassFilters() {
    return myClassFilters != null ? myClassFilters : ClassFilter.EMPTY_ARRAY;
  }

  public final boolean setClassFilters(ClassFilter[] classFilters) {
    boolean changed = !filtersEqual(myClassFilters, classFilters);
    myClassFilters = classFilters;
    return changed;
  }

  protected static boolean filtersEqual(Object[] a, Object[] b) {
    if ((a == null || a.length == 0) && (b == null || b.length == 0)) {
      return true;
    }
    return Arrays.equals(a, b);
  }

  @XCollection(propertyElementName = "class-exclusion-filters")
  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters != null ? myClassExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public boolean setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    boolean changed = !filtersEqual(myClassExclusionFilters, classExclusionFilters);
    myClassExclusionFilters = classExclusionFilters;
    return changed;
  }

  public static TraceSettings getInstance() {
    return ApplicationManager.getApplication().getService(TraceSettings.class);
  }

  @Override
  public void loadState(@NotNull TraceSettings state) {
    myClassFilters = state.getClassFilters();
    myClassExclusionFilters = state.getClassExclusionFilters();
  }

  @Override
  public TraceSettings getState() {
    return this;
  }
}
