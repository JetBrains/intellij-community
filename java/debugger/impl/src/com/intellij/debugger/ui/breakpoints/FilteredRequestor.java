// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.ui.classFilter.ClassFilter;

public interface FilteredRequestor extends LocatableEventRequestor {
  @Override
  String getSuspendPolicy();

  boolean isInstanceFiltersEnabled();
  InstanceFilter[] getInstanceFilters();

  boolean isCountFilterEnabled();
  int getCountFilter();

  boolean isClassFiltersEnabled();
  ClassFilter[] getClassFilters();
  ClassFilter[] getClassExclusionFilters();

  default boolean isConditionEnabled() {
    return false;
  }
}
